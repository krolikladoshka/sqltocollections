package syntax

import dataselector.interpreter.Scope
import scanner.Token
import scanner.TokenType

enum class SortDirection {
    Ascending,
    Descending
}

fun sortDirectionFromTokenType(tokenType: TokenType): SortDirection {
    return when (tokenType) {
        TokenType.Asc -> SortDirection.Ascending
        TokenType.Desc -> SortDirection.Descending
        else -> throw IllegalArgumentException(
            "Unexpected sort direction $tokenType"
        )
    }
}

data class OrderBy(
    val column: Ast.Expression.Identifier,
    val direction: SortDirection = SortDirection.Ascending
)



sealed class Ast {
    sealed class Statement {
        data class Select(val query: Expression.Select)
    }

    sealed class Expression(
        var alias: Token? = null
    ) {
        open fun schematicPrint(): String {
            return buildString {
                append("Expression@${this@Expression.javaClass.canonicalName.split('.').last()}")
                this@Expression.alias?.let {
                    " as $it"
                }
            }
        }

        interface Selectable

        data class Grouping(
            val expression: Expression
        ) : Expression() {
            override fun schematicPrint(): String {
                return "(${this.expression.schematicPrint()})"
            }
        }

        data class Identifier(
            val name: Token,
            val table: Token? = null
        ) : Expression() {
            override fun schematicPrint(): String {
                return buildString {
                    this@Identifier.table?.let {
                        append("${it.lexeme}.")
                    }
                    append(this@Identifier.name.lexeme)
                }
            }
        }

        data class TableIdentifier(
            val name: Token,
        ) : Expression(), Selectable

        data class Between(
            val token: Token,
            val value: Expression,
            val start: Expression,
            val end: Expression
        ) : Expression()

        data class Like(
            val token: Token,
            val value: Expression,
            val right: Expression
        ) : Expression()

        data class In(
            val token: Token,
            val value: Expression,
            val range: List<Expression>
        ) : Expression()

        data class Unary(
            val token: Token,
            val right: Expression
        ) : Expression()

        data class Binary(
            val left: Expression,
            val operator: Token,
            val right: Expression
        ) : Expression() {
            override fun schematicPrint(): String {
                return buildString {
                    append(this@Binary.left.schematicPrint())
                    append(" ")
                    append(this@Binary.operator.tokenType.name.lowercase())
                    append(" ")
                    append(this@Binary.right.schematicPrint())
                }
            }
        }

        data class Logical(
            val left: Expression,
            val operator: Token,
            val right: Expression
        ) : Expression()

        data class ExistSubquery(
            val token: Token,
            val subquery: Select
        ) : Expression()

        data class JoinClause(
            val joinType: Token,
            val tableReference: TableReference,
            val condition: Expression?
        )

        data class Select(
            val token: Token,
            val starSelect: Boolean = false,
            val results: List<Expression>,
            val from: TableReference? = null,
            val joins: List<JoinClause> = listOf(),
            val where: Expression? = null,
            val groupBy: List<Expression> = listOf(),
            val having: Expression? = null,
            val orderBy: List<OrderBy> = listOf(),
            val limit: Expression? = null,
            val offset: Expression? = null,
            val scope: Scope? = null
        ) : Expression(), Selectable

        data class TableReference(
            val token: Token,
            val table: Selectable,
        ) : Expression()

        data class Literal(
            val token: Token,
            val value: Any?,
        ) : Expression() {
            override fun toString(): String {
                return this.value.toString()
            }

            override fun schematicPrint(): String {
                return this.toString()
            }
        }

        data class Case(
            val token: Token,
            val whens: List<Expression>,
            val thens: List<Expression>,
            val elseBranch: Expression
        ) : Expression()

        data class Call(
            val callee: Expression,
            val token: Token,
            val arguments: List<Expression>,
            val starArgument: Boolean = false
        ) : Expression()
    }
}