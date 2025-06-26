package syntax

import dataselector.interpreter.Column
import parser.ParseException
import scanner.TokenType


//sealed class NamedExpression {
//
//
//    data class Expression(
//        val expressionView()
//        val name: String
//    ) : NamedExpression()
//
//    data class SimpleName(
//        val name: String
//    ) : NamedExpression()
//}


data class Identifier(
    val table: String?,
    val name: String,
)

sealed class Projection(
    val alias: String?
) {
    class ColumnIdentifier(
        val identifier: Identifier,
        alias: String?
    ) : Projection(alias)

    class Expression(
        val expression: Ast.Expression,
        alias: String?
     ) : Projection(alias)

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
)

data class Source {}
data class Join {}
data class Where {}
data class Sort {}

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
                this@Factory.getGroupByColumn(groupBy)
            }
            val having = if (query.having != null) {
                this.splitCondition(query.having)
            } else {
                Conditions(listOf(), listOf())
            }

            var limit: Number?;
            var offset: Number;

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
                    Source(source.table.name.lexeme)
                is Ast.Expression.Select ->
                    Source(source.alias?.lexeme, this.fromQuery(source.table))
                else ->
                    throw ParseException("Unexpected source in from clause ${source.table}")
            }
        }

        private fun getJoin(join: Ast.Expression.JoinClause): Join {
            return Join(
                joinTypeFromTokenType(join.joinType.tokenType),
                join.condition?.let { this@Factory.splitCondition(it) },
                this.getSource(join.tableReference),
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
                        this.splitCondition(expression.right)
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

        private fun getGroupByColumn(groupBy: Ast.Expression): Identifier {
            return when (groupBy) {
                is Ast.Expression.Identifier ->
                    Identifier(groupBy.table?.lexeme, groupBy.name.lexeme)
                else ->
                    throw ParseException(
                        "Only column expressions are supported in group by for now"
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