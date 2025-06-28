package syntax

import parser.ParseException
import scanner.TokenType


data class Identifier(
    val table: String?,
    val name: String,
) {
    override fun toString(): String {
        return buildString {
            this@Identifier.table?.let {
                append("$it.")
            }
            append(this@Identifier.name)
        }
    }
}

sealed class Projection(
    val alias: String?
) {
    override fun toString(): String {
        return when (this) {
            is ColumnIdentifier -> buildString {
                append(this@Projection.identifier)
                this@Projection.alias?.let {
                    append(" as $it")
                }
            }
            is Expression -> this@Projection.schematicPrint()
            is Subquery -> buildString {
                append("Subquery")
                this@Projection.alias?.let {
                    append(" as $it")
                }
            }
        }
    }

    class ColumnIdentifier(
        val identifier: Identifier,
        alias: String?
    ) : Projection(alias)

    class Expression(
        val expression: Ast.Expression,
        alias: String?
    ) : Projection(alias) {
        fun schematicPrint(): String {
            return buildString {
                append(this@Expression.expression.schematicPrint())
                this@Expression.alias?.let {
                    append(" as $it")
                }
            }
        }
    }

    class Subquery(
        val select: EcwidSelectView,
        alias: String?
    ) : Projection(alias)
}

enum class JoinType {
    Left,
    Inner,
    Cross
}

private fun joinTypeFromTokenType(tokenType: TokenType): JoinType {
    return when (tokenType) {
        TokenType.Left ->
            JoinType.Left
        TokenType.Cross ->
            JoinType.Cross
        TokenType.Inner ->
            JoinType.Inner
        else ->
            throw ParseException(
                "Unexpected join type $tokenType"
            )
    }
}
enum class BinaryLogical {
    Or,
    And
}

fun binaryLogicalFromTokenType(tokenType: TokenType): BinaryLogical {
    return when (tokenType) {
        TokenType.Or ->
            BinaryLogical.Or
        TokenType.And ->
            BinaryLogical.And
        else ->
            throw ParseException(
                "Unexpected binary logical $tokenType"
            )
    }
}

data class Conditions(
    val operands: List<Ast.Expression>,
    val operators: List<BinaryLogical>
) {
    override fun toString(): String {
        return buildString {
            this@Conditions.operands.forEachIndexed {
                index, operand ->
                    append(operand.schematicPrint())
                    if (index < this@Conditions.operators.size) {
                        append(" ")
                        append(this@Conditions.operators[index].name.lowercase())
                        append(" ")
                    }
            }
        }
    }
}

sealed class Source(
    val alias: String?
) {
    override fun toString(): String {
        return this.prettyPrint(0, 1)
    }

    fun prettyPrint(currentDepth: Int, maxDepth: Int): String {
        return when (this) {
            is Table -> buildString {
                append(this@Source.name)
                this@Source.alias?.let {
                    append(" as $it")
                }
            }
            is SubqueryTable -> buildString {
                append("(")
                if (currentDepth >= maxDepth) {
                    append("Subquery")
                } else {
                    append("\n")
                    append(this@Source.select.prettyPrint(currentDepth, maxDepth))
                    append("\n")
                    if (currentDepth > 0) {
                        append("  ".repeat(currentDepth - 1))
                    }
                }
                append(")")
                this@Source.alias?.let {
                    append(" as $it")
                }
            }
        }
    }

    class Table(
        val name: String,
        alias: String?
    ) : Source(alias)

    class SubqueryTable(
        val select: EcwidSelectView,
        alias: String?
    ) : Source(alias)
}


data class Join(
    val joinType: JoinType,
    val source: Source,
    val on: Conditions?
)

data class Sort(
    val identifier: Identifier,
    val direction: SortDirection = SortDirection.Ascending
)


class EcwidSelectView(
    val projections: List<Projection>,
    val fromSource: Source?,
    val joins: List<Join>,
    val whereClauses: Conditions,
    val groupByColumns: List<Identifier> = listOf(),
    val havingClauses: Conditions,
    val sortColumns: List<Sort> = listOf(),
    val limit: Int? = null,
    val offset: Int = 0,
    val isStarSelect: Boolean = false
) {
    override fun toString(): String {
        return this.prettyPrint(currentDepth = 0, maxDepth = 1)
    }

    fun prettyPrint(currentDepth: Int = 0, maxDepth: Int = 0): String {
        fun walk(sb: StringBuilder, currentDepth: Int) {
            if (currentDepth >= maxDepth) {
                return
            }
            val tab = "  ".repeat(currentDepth)
            val selectTab = "  ".repeat(currentDepth + 1)
            sb.append("${tab}select\n$selectTab")

            val projections = this.projections.joinToString("\n$selectTab") { projection ->
                projection.toString()
            }
            sb.append(projections)

            if (fromSource == null) {
                return
            }
            sb.append("\n")
            sb.append(tab)
            sb.append("from ")
            sb.append(fromSource.prettyPrint(currentDepth + 1, maxDepth))
            sb.append("\n")

            val joins = this.joins.joinToString("\n") { join ->
                buildString {
                    append(tab)
                    append(join.joinType.name)
                    append(" join ")
                    append(join.source.prettyPrint(currentDepth + 1, currentDepth))
                    append(tab)
                    if (join.on != null) {
                        append("\n")
                        append(tab)
                        append("  ")
                        append("on ")
                        append(join.on)
                    }
                }
            }
            sb.append(joins)

            if (this.joins.isNotEmpty()) {
                sb.append("\n")
            }
            sb.append(tab)
            sb.append("where ")
            sb.append(this.whereClauses)
            sb.append("\n")

            if (this.groupByColumns.isNotEmpty()) {
                sb.append(tab)
                sb.append("group by ")
                val groupByColumns = this.groupByColumns.joinToString(", ") {
                    groupBy ->
                        groupBy.toString()
                }
                sb.append(groupByColumns)
                sb.append("\n")
                sb.append(tab)
                sb.append("having ")
                sb.append(this.havingClauses)
            }

            if (this.sortColumns.isNotEmpty()) {
                sb.append("\n")
                sb.append(tab)
                sb.append("order by ")
                val sort = this.sortColumns.joinToString(", ") {
                    orderBy -> orderBy.toString()
                }
                sb.append(sort)
            }

            this.limit?.let {
                sb.append("\n")
                sb.append(tab)
                sb.append("limit $it")
            }
            sb.append("\n")
            sb.append(tab)
            sb.append("offset ${this.offset}")
        }

        return buildString {
            walk(this, currentDepth)
        }
    }

    companion object Factory {
        fun fromAst(ast: Ast.Statement.Select): EcwidSelectView {
            val query = ast.query

            return this.fromQuery(query)
        }

        fun fromQuery(query: Ast.Expression.Select): EcwidSelectView {
            val projections = this.getProjections(query.results)
            val from = query.from?.let {
                this@Factory.getSource(it)
            }
            val joins = query.joins.map { join ->
                this@Factory.getJoin(join)
            }
            val whereClauses = if (query.where != null) {
                this.splitCondition(query.where)
            } else {
                Conditions(listOf(), listOf())
            }

            val groupByColumns = query.groupBy.map { groupBy ->
                this@Factory.getExpressionColumn(groupBy)
            }
            val having = if (query.having != null) {
                this.splitCondition(query.having)
            } else {
                Conditions(listOf(), listOf())
            }
            val sort = query.orderBy.map {
                orderBy -> Sort(
                    Identifier(
                        orderBy.column.table?.lexeme,
                        orderBy.column.name.lexeme
                    ),
                    orderBy.direction
                )
            }
            var limit: Number?
            var offset: Number

            try {
                limit = query.limit?.let {
                    this@Factory.getValue(it)
                }

                offset = if (query.offset != null) {
                    this@Factory.getValue(query.offset)
                } else {
                    0
                }
            } catch (e: ParseException) {
                throw ParseException(
                    e.message + "\n" +
                    "I'm not sure because I actually allow expressions in limit/offset"
                )
            }

            return EcwidSelectView(
                projections,
                from,
                joins,
                whereClauses,
                groupByColumns,
                having,
                sort,
                limit?.toInt(),
                offset.toInt()
            )
        }

        private fun getProjections(selectResults: List<Ast.Expression>): List<Projection> {
            return selectResults.map {
                resultExpression -> when(resultExpression) {
                    is Ast.Expression.Identifier ->
                        Projection.ColumnIdentifier(
                            Identifier(resultExpression.table?.lexeme, resultExpression.name.lexeme),
                            resultExpression.alias?.lexeme
                        )
                    is Ast.Expression.Select ->
                        Projection.Subquery(
                            this@Factory.fromQuery(resultExpression),
                            resultExpression.alias?.lexeme
                        )
                    else ->
                        Projection.Expression(
                            resultExpression,
                            resultExpression.alias?.lexeme
                        )
                }
            }
        }

        private fun getSource(source: Ast.Expression.TableReference): Source {
            return when (source.table) {
                is Ast.Expression.TableIdentifier ->
                    Source.Table(
                        source.table.name.lexeme, source.alias?.lexeme
                    )
                is Ast.Expression.Select ->
                    Source.SubqueryTable(
                        this.fromQuery(source.table),
                        source.alias?.lexeme,
                    )
                else ->
                    throw ParseException(
                        "Unexpected source in from clause ${source.table}"
                    )
            }
        }

        private fun getJoin(join: Ast.Expression.JoinClause): Join {
            return Join(
                joinTypeFromTokenType(join.joinType.tokenType),
                this.getSource(join.tableReference),
                join.condition?.let { this@Factory.splitCondition(it) },
            )
        }

        private fun splitCondition(condition: Ast.Expression): Conditions {
            val operands = mutableListOf<Ast.Expression>()
            val operators = mutableListOf<BinaryLogical>()

            fun walk(expression: Ast.Expression) {
                when (expression) {
                    is Ast.Expression.Logical -> {
                        walk(expression.left)
                        val operator = expression.operator.tokenType
                        operators.add(binaryLogicalFromTokenType(operator))
                        walk(expression.right)
                    }
                    else ->
                        operands.add(expression)
                }
            }

            walk(condition)

            return Conditions(
                operands,
                operators
            )
        }

        private fun getExpressionColumn(expression: Ast.Expression): Identifier {
            return when (expression) {
                is Ast.Expression.Identifier ->
                    Identifier(expression.table?.lexeme, expression.name.lexeme)
                else ->
                    throw ParseException(
                        "Only column expressions were expected"
                    )
            }
        }

        private fun getValue(value: Ast.Expression): Number {
            return when (value) {
                is Ast.Expression.Literal ->
                    value.value as Number
                else ->
                    throw ParseException(
                        "Expected literal"
                    )
            }
        }
    }
}