package dataselector

import dataselector.interpreter.*
import scanner.TokenType
import syntax.Ast
import syntax.OrderBy
import syntax.SortDirection


class Interpreter {
    companion object Functions {
        val aggregationFunctions = mapOf(
            "avg" to AvgCall(),
            "sum" to SumCall(),
            "count" to CountCall()
        )

        val helperFunction = mapOf(
            "round" to RoundCall(),
            "toint" to ToIntCall()
        )
    }

    var currentRow: Row?

    private var selectsCounter: Int
    private val rootScope: Scope
    private var currentScope: Scope
    private var prevScope: Scope?

    constructor(bindings: Map<String, Table>) {
        this.currentRow = null
        this.selectsCounter = 0

        this.rootScope = Scope(ExecutionContext(bindings))
        this.currentScope = this.rootScope
        this.prevScope = null
    }

    private fun generateSubqueryAlias(): String {
        while (true) {
            val alias = "t${this.selectsCounter}"
            this.selectsCounter++

            if (this.currentScope.getTableAlias(alias) == null) {
                return alias
            }
        }
    }

    private fun stackScope(block: () -> Any?): Any? {
        val prevScope = this.currentScope
        this.currentScope = this.currentScope.stack()

        val result = block()

        this.currentScope = prevScope

        return result
    }

    fun execute(
        query: Ast.Statement.Select, bindings: Map<String, Table> = mapOf()
    ): Table
    {
        println("Starting execution of $query with bindings $bindings")

        this.rootScope.updateLocalBindings(bindings)

        return this.executeSelectExpression(query.query)
    }

    private fun executeSelectExpression(
        query: Ast.Expression.Select, subquery: Boolean = false
    ): Table {
        if (query.starSelect) {
            throw ExecutionException("Star select isn't supported! ${query.token.at()}")
        }

        if (query.from == null) {
            val row = this.evaluateSelectResultsForRow(query)

            return Table("result", row.columns.map { c -> c.name }, listOf(row))
        }

        var sequence = this.evaluateFrom(query.from)
        if (!subquery) {
            sequence.scope = this.rootScope
        }

        this.currentRow = sequence.first()
        sequence = this.evaluateJoins(query.joins, sequence)

        query.where?.let {
            sequence = sequence.mapRows {
                 filter {
                     row ->
                         this@Interpreter.currentRow = row
                         this@Interpreter.evaluateExpression(it) as Boolean
                 }
            }
        }

        sequence = if (query.groupBy.isNotEmpty()) {
            this.evaluateGroupBy(query, sequence)
        } else {
            sequence.mapRows {
                map {
                    row ->
                        this@Interpreter.currentRow = row
                        this@Interpreter.evaluateSelectProjection(query,sequence)
                }
            }

        }

        if (query.orderBy.isNotEmpty()) {
            sequence = this.evaluateOrderBy(query.orderBy, sequence)
        }
        query.offset?.let {
            val offset = (this@Interpreter.evaluateExpression(it) as Number).toInt()

            require(offset >= 0) {
                "Offset should be a positive integer"
            }

            sequence = sequence.mapRows {
                drop(offset)
            }
        }

        query.limit?.let {
            val limit = (this@Interpreter.evaluateExpression(it) as Number).toInt()

            require(limit >= 0) {
                "Limit should be a positive integer"
            }

            sequence = sequence.mapRows {
                take(limit)
            }
        }

        return sequence
    }

    private fun evaluateOrderBy(orderByColumns: List<OrderBy>, table: Table): Table {
        require(orderByColumns.isNotEmpty()) {
            "No order by columns provided in order by clause"
        }

        val firstSort = orderByColumns.first()
        var comparator = compareBy<Row> {
            row ->
                this@Interpreter.currentRow = row
                this@Interpreter.evaluateIdentifier(firstSort.column) as Comparable<Any?>
        }
        if (firstSort.direction == SortDirection.Descending) {
            comparator = comparator.reversed()
        }
        for (orderBy in orderByColumns.drop(1)) {
            comparator.thenBy {
                row ->
                    this@Interpreter.currentRow = row
                    this@Interpreter.evaluateIdentifier(orderBy.column) as Comparable<Any?>
            }

            if (orderBy.direction == SortDirection.Descending) {
                comparator = comparator.reversed()
            }
        }

        val table = table.copy(rows=table.sortedWith(comparator))
        this.currentScope.setTable(table.name, table)

        return table
    }

    private fun evaluateGroupBy(query: Ast.Expression.Select, table: Table): Table {
        require(query.groupBy.isNotEmpty()) {
            "Expected at least 1 aggregation in group by"
        }
        require(query.groupBy.all {
            expr -> expr is Ast.Expression.Identifier
        }) {
            "The only idea I came up with is to perform structural equality check during parsing or "
            "semantics analysis. I didn't want to implement it, so\n"
            "Only identifiers groupby expressions are supported for now . . . "
            "and probably forever. Use subqueries . . ."
        }

        val grouped = table.groupBy {
            row -> query.groupBy.map {
                groupBy ->
                    this@Interpreter.currentRow = row
                    val key = this@Interpreter.evaluateIdentifier(
                        groupBy as Ast.Expression.Identifier
                    )
                    Pair(
                        key,
                        groupBy
                    )
            }.map {
                (key, groupBy) -> when (key) {
                    is String -> InnerGroupingKey.String(key, groupBy)
                    is Number -> InnerGroupingKey.Number(key, groupBy)
                    null -> InnerGroupingKey.Null(groupBy)
                    else -> throw ExecutionException(
                        "Can't use $key as grouping key in group by"
                    )
                }
            }.toList()
        }

        var groupByProjection = this.evaluateAggregatedSelectProjection(
            query, grouped, table
        )
        this.currentScope.setTable(groupByProjection.name, groupByProjection)
        query.having?.let {
            groupByProjection = groupByProjection.mapRows {
                filter {
                    row ->
                        this@Interpreter.currentRow = row
                        this@Interpreter.evaluateExpression(it) as Boolean
                }
            }
        }

        return groupByProjection
    }

    private fun evaluateAggregatedSelectProjection(
        query: Ast.Expression.Select,
        groups: Map<GroupingKey, List<Row>>, table: Table
    ): Table {
        require(query.results.all {
            projection -> projection is Ast.Expression.Identifier || projection is Ast.Expression.Call
        }) {
            "Only grouping keys and aggregation expressions are allowed in group by query"
        }

        val tableName = "${table.name}_groupby"
        val result = mutableListOf<Row>()

        for ((groupKey, group) in groups.entries) {
            val columns = groupKey.mapIndexed {
                index, key -> Column(
                    if (key.identifier.table == null) {
                        ""
                    } else {
                        key.identifier.table!!.lexeme
                    },
                    key.identifier.name.lexeme,
                    key.identifier.name.lexeme,
                    index
                )
            }.toMutableList()

            val keyValues = columns.map {
                column ->
                    val firstRow = group.first()
                    firstRow.get(column.alias, column.tableAlias)
            }

            columns += listOf(Column(
                tableName,
                "__${tableName}_group__",
                "__${tableName}_group__",
                groupKey.size
            ))

            val values = keyValues +  listOf(group)
            this.currentRow = Row(
                tableName,
                columns,
                values
            )

            val projected = query.results.mapIndexed {
                index, selectProjection -> when (selectProjection) {
                    is Ast.Expression.Identifier -> {
                        val value = this.evaluateIdentifier(selectProjection)
                        if (selectProjection.alias == null) {
                            Pair(
                                Column(tableName, "$index", "$index", index),
                                value
                            )
                        } else {
                            Pair(
                                Column(
                                    tableName,
                                    selectProjection.alias!!.lexeme,
                                    selectProjection.name.lexeme,
                                    index
                                ),
                                value
                            )
                        }
                    }
                    is Ast.Expression.Call -> {
                        val value = this.evaluateAggregateCall(
                            selectProjection, group
                        )

                        if (selectProjection.alias == null) {
                            Pair(
                                Column(tableName, "$index", "$index", index), value
                            )
                        }
                        else {
                            Pair(
                                Column(
                                    tableName, selectProjection.alias!!.lexeme,
                                    "$index", index
                                ),
                                value
                            )
                        }
                    }
                    else -> throw ExecutionException(
                        "Only grouping keys and aggregation expressions are allowed in group by query"
                    )
                }
            }.unzip()

            result.add(Row(
                tableName,
                projected.first,
                projected.second
            ))
        }

        return Table(
            tableName,
            if (result.isNotEmpty()) {
                result.first().columns.map {
                        c -> c.alias
                }
            } else {
                listOf()
            },
            result,
            this.currentScope
        )
    }

    fun evaluateAggregateCall(selectProjection: Ast.Expression.Call, group: List<Row>): Any? {
        require(selectProjection.callee is Ast.Expression.Identifier) {
            "Unexpected calee expression"
        }
        require(aggregationFunctions.contains(selectProjection.callee.name.lexeme.lowercase())) {
            "Unknown aggregate function ${selectProjection.callee.name}"
        }
        require(selectProjection.arguments.all {
            arg -> arg is Ast.Expression.Identifier
        }) {
            "Only identifiers are allowed as arguments for aggregation functions for now. . . and forever"
        }

        val function = aggregationFunctions[selectProjection.callee.name.lexeme.lowercase()]
        val args = selectProjection.arguments.map {
            arg -> arg as Ast.Expression.Identifier
        }
        val result = function!!.call(this, args, group)

        return result
    }

    private fun evaluateJoins(joins: List<Ast.Expression.JoinClause>, table: Table): Table {
       return joins.fold(table) {
            joinedTable, join -> this@Interpreter.evaluateJoin(join, joinedTable)
        }
    }

    private fun evaluateJoin(joinClause: Ast.Expression.JoinClause, table: Table): Table {
        require(joinClause.joinType.tokenType == TokenType.Cross || joinClause.condition != null) {
            "on condition is required for join"
        }

        val rightTable = this.evaluateFrom(joinClause.tableReference, true)

        val onCondition = if (joinClause.joinType.tokenType == TokenType.Cross) {
            fun (_: Row, _: Row): Boolean {
                return true
            }
        } else {
            fun(left: Row, right: Row): Boolean {
                this.currentRow = left.join(right, table.name, rightTable.name)
                // TODO: Design issue, row should be passed to evaluate expression, evaluateExpressionOnRow?
                // don't want to add visitors
                return this@Interpreter.evaluateExpression(
                    joinClause.condition!!
                ) as Boolean
            }
        }

        val table = when (joinClause.joinType.tokenType) {
            TokenType.Cross -> table.crossJoin(rightTable)
            TokenType.Left -> table.leftJoin(rightTable, onCondition)
            TokenType.Inner -> table.innerJoin(rightTable, onCondition)
            else -> throw ExecutionException(
                "Unsupported join type ${joinClause.joinType.tokenType}"
            )
        }
        table.scope = this.currentScope

        return table
    }

    private fun evaluateFrom(from: Ast.Expression.TableReference, inJoin: Boolean = false): Table {
        val table = when (from.table) {
            is Ast.Expression.TableIdentifier -> {
                var table = this.currentScope.getByIdentifier(from.table)

                if (table == null) {
                    throw ExecutionException(
                        "Table ${from.table.name} is not found in current scope"
                    )
                }
                table.scope = this.currentScope

                if (inJoin) {
                    if (from.alias == null) {
                        throw ExecutionException("Join tables should always be aliased")
                    }
                    table = table.copy()
                    table.setTableName(from.alias!!.lexeme)
                }
                table
            }
            is Ast.Expression.Select -> {
                val table = this.stackScope {
                    val selectSequence = this@Interpreter.executeSelectExpression(from.table)

                    selectSequence
                } as Table
                table.scope = this.currentScope

                if (from.alias?.lexeme == null) {
                    throw ExecutionException("Select subquery at ${from.token} should be aliased")
                }

                if (this.currentScope.getLocalTableByName(from.alias!!.lexeme) != null) {
                    throw ExecutionException(
                        "Table ${table.name} is already defined in this scope ${from.token.at()}"
                    )
                }
                table.setTableName(from.alias!!.lexeme)
                table
            }
            else -> throw ExecutionException(
                "Expected a table reference or select statement in from clause"
            )
        }

        val alias = if (from.alias?.lexeme == null) {
            this.generateSubqueryAlias()
        } else {
            if (this.currentScope.getTableAlias(from.alias!!.lexeme) != null) {
                throw ExecutionException(
                    "Alias ${from.alias} is already defined in current scope"
                )
            }
            from.alias!!.lexeme
        }
        this.currentScope.setTable(table.name, table)
        this.currentScope.setTableAlias(alias, table.name)

        return table
    }

    private fun evaluateSelectResultsForRow(select: Ast.Expression.Select): Row {
        val entries = select.results.mapIndexed {
            index, expr ->
                val value = this.evaluateExpression(expr)
                if (expr.alias == null) {
                    Pair(
                        Column("","$index", "$index", index),
                        value
                    )
                } else {
                    Pair(
                        Column("", expr.alias!!.lexeme, "$index", index),
                        value
                    )
                }
        }.unzip()

        return Row("", entries.first, entries.second)
    }

    private fun evaluateSelectProjection(select: Ast.Expression.Select, table: Table): Row {
        val columns = mutableListOf<Column>()
        val values = mutableListOf<Any?>()
        val tableName = if (select.alias?.lexeme != null) {
            select.alias!!.lexeme
        } else {
            table.name
        }

        select.results.mapIndexed {
            index, expr ->
                val value = this.evaluateExpression(expr)
                val result = if (expr.alias == null) {
                    Pair(
                        Column(tableName,"$index", "$index", index),
                        value
                    )

                } else {
                    Pair(
                        Column(tableName, expr.alias!!.lexeme, "$index", index),
                        value
                    )
                }
                result
        }.forEach {
            cv ->
                columns.add(cv.first)
                values.add(cv.second)
        }
        // don't look
        return Row(tableName, columns, values)
    }

    private fun evaluateTableIdentifier(identifier: Ast.Expression.TableIdentifier): Table {
        val table = this.currentScope.getByIdentifier(
            identifier
        )

        if (table == null) {
            throw ExecutionException(
                "Couldn't find table identifier $identifier "
            )
        }

        return table
    }
    private fun evaluateIdentifier(identifier: Ast.Expression.Identifier): Any? {
        require(this.currentRow != null) {
            "Unresolved variable"
        }

        if (identifier.table?.lexeme != null) {
            val tableName = this.currentScope.getTableAlias(identifier.table.lexeme)

            return this.currentRow!!.get(identifier.name.lexeme, tableName!!)
        }

        val columnName = if (identifier.alias?.lexeme != null) {
            identifier.alias!!.lexeme
        } else {
            identifier.name.lexeme
        }

        val columns = mutableListOf<Table>()
        for (table in this.currentScope.getLocalTables()) {
            if (table.fields.contains(columnName)) {
                columns.add(table)
            }
        }

        if (columns.isEmpty()) {
            throw ExecutionException("Column $identifier is not defined")
        } else if (columns.size > 1) {
            throw ExecutionException("Ambiguous column $identifier")
        }

        return this.currentRow!!.get(columnName, columns[0].name)
    }

    private fun evaluateEquals(left: Any?, right: Any?): Boolean {
        if (left == null && right == null) {
            return true
        }
        return when (left) {
            is Number -> left.toDouble() == (right as Number).toDouble()
            is String -> left == (right as String)
            else ->
                (left as Comparable<Any?>) == (right as Comparable<Any?>)
        }
    }

    private fun evaluateIs(expression: Ast.Expression.Is): Boolean {
        val value = this.evaluateExpression(expression.left)

        return value == null
    }

    private fun evaluateBinary(expression: Ast.Expression.Binary): Any? {
        val left = this.evaluateExpression(expression.left)
        val right = this.evaluateExpression(expression.right)

        try {
            val eval = when (expression.operator.tokenType) {
                TokenType.Plus -> (left as Number).toDouble() + (right as Number).toDouble()
                TokenType.Minus -> (left as Number).toDouble() - (right as Number).toDouble()
                TokenType.Star -> (left as Number).toDouble() * (right as Number).toDouble()
                TokenType.Slash -> (left as Number).toDouble() / (right as Number).toDouble()
                TokenType.Less -> (left as Number).toDouble() < (right as Number).toDouble()
                TokenType.LessEquals -> (left as Number).toDouble() <= (right as Number).toDouble()
                TokenType.Greater -> (left as Number).toDouble() > (right as Number).toDouble()
                TokenType.GreaterEquals -> (left as Number).toDouble() >= (right as Number).toDouble()
                TokenType.Equals -> this.evaluateEquals(left, right)
                TokenType.NotEquals -> !this.evaluateEquals(left, right)
//                TokenType.Less -> (left as Comparable<Number?>) < (right as Comparable<Any?>)
//                TokenType.LessEquals -> (left as Comparable<Any?>) <= (right as Comparable<Any?>)
//                TokenType.Greater -> (left as Comparable<Any?>) > (right as Comparable<Any?>)
//                TokenType.GreaterEquals -> (left as Comparable<Any?>) >= (right as Comparable<Any?>)
//                TokenType.Equals -> (left as Comparable<Any?>) == (right as Comparable<Any?>)
//                TokenType.NotEquals -> (left as Comparable<Any?>) != (right as Comparable<Any?>)
                else -> throw ExecutionException(
                    "Unknown binary operation ${expression.operator.tokenType}"
                )
            }

            return eval
        } catch (e: Exception) {
            throw ExecutionException(
                "Failed to evaluate ${expression}; $e"
            )
        }
    }

    private fun evaluateLogical(expression: Ast.Expression.Logical): Boolean {
        val left = this.evaluateExpression(expression.left) as Boolean

        return when (expression.operator.tokenType) {
            TokenType.Or ->
                if (left) {
                    true
                } else {
                    this.evaluateExpression(expression.right) as Boolean
                }
            TokenType.And ->
                if (left) {
                    this.evaluateExpression(expression.right) as Boolean
                } else {
                    false
                }
            else ->
                throw ExecutionException(
                    "Unknown logical expression ${expression.operator.tokenType}"
                )
        }
    }

    fun evaluateUnary(expression: Ast.Expression.Unary): Any? {
        val value = this.evaluateExpression(expression.right)

        return when (expression.token.tokenType) {
            TokenType.Not -> !(value as Boolean)
            TokenType.Minus -> -(value as Number).toDouble()
            else -> throw ExecutionException(
                "Unexpected unary operator ${expression.token.tokenType}"
            )
        }
    }

    private fun evaluateIn(expression: Ast.Expression.In): Boolean {
        require(expression.token.tokenType == TokenType.In) {
            "Expected 'in' keyword in 'in' expression"
        }
        val value = this.evaluateExpression(expression.value)

        return value in expression.range.map {
            expr -> this@Interpreter.evaluateExpression(expr)
        }
    }

    private fun evaluateBetween(expression: Ast.Expression.Between): Boolean {
        val value = this.evaluateExpression(expression.value) as Comparable<Any?>
        val start = this.evaluateExpression(expression.start) as Comparable<Any?>
        val end = this.evaluateExpression(expression.end) as Comparable<Any?>

        return (value >= start) && (value <= end)
    }

    private fun evaluateCase(expression: Ast.Expression.Case): Any? {
        require(expression.whens.size == expression.thens.size) {
            "'when' statements has to match 'then' statements in 'case' expression"
        }

        val whenThen = expression.whens.zip(expression.thens)

        for ((whenExpression, thenExpression) in whenThen) {
            val evaluatedWhen = this.evaluateExpression(whenExpression) as Boolean

            if (evaluatedWhen) {
                return this.evaluateExpression(thenExpression)
            }
        }

        return this.evaluateExpression(expression.elseBranch)
    }

    fun evaluateCall(call: Ast.Expression.Call): Any? {
        require(call.callee is Ast.Expression.Identifier) {
            "Unexpected callee expression ${call.token}"
        }

        val functionName = call.callee.name.lexeme
        if (aggregationFunctions.contains(functionName.lowercase())) {
            throw ExecutionException(
                "Aggregation expressions are not allowed outside of group by queries" +
                "${call.callee.name}"
            )
        }

        if (!helperFunction.contains(functionName.lowercase())) {
            throw ExecutionException(
                "Unknown function ${call.callee.name}"
            )
        }

        return helperFunction[functionName.lowercase()]!!.call(
            this, call.arguments
        )
    }

    fun evaluateExpression(expression: Ast.Expression): Any? {
        return when (expression) {
            is Ast.Expression.Literal ->
                expression.value
            is Ast.Expression.TableIdentifier ->
                this.evaluateTableIdentifier(expression)
            is Ast.Expression.Identifier ->
                this.evaluateIdentifier(expression)
            is Ast.Expression.Binary ->
                this.evaluateBinary(expression)
            is Ast.Expression.Logical ->
                this.evaluateLogical(expression)
            is Ast.Expression.Unary  ->
                this.evaluateUnary(expression)
            is Ast.Expression.In ->
                this.evaluateIn(expression)
            is Ast.Expression.Between ->
                this.evaluateBetween(expression)
            is Ast.Expression.Case ->
                this.evaluateCase(expression)
            is Ast.Expression.Call ->
                this.evaluateCall(expression)
            is Ast.Expression.Grouping ->
                this.evaluateExpression(expression.expression)
            is Ast.Expression.Is ->
                this.evaluateIs(expression)
            else -> throw ExecutionException(
                "Unknown expression"
            )
        }
    }
}