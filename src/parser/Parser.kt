package parser

import scanner.Token
import scanner.TokenType
import syntax.Ast
import syntax.OrderBy
import syntax.SortDirection
import syntax.sortDirectionFromTokenType


open class ParseException(message: String) : RuntimeException(message)

class UnexpectedTokenException(
    token: Token,
    message: String
) : ParseException("$token $message")

class Parser(
    private val tokens: List<Token>,
    private var current: Int = 0,
) {
    private val currentLine: Int
        get() {
            return this.peek().line
        }

    private val currentColumn: Int
        get() {
            return this.peek().column
        }

    private val currentPosition: String
        get() {
            return "${this.currentLine}:${currentColumn}"
        }

    private fun skipComments() {
        while (!this.isAtEnd() && this.tokens[this.current].tokenType == TokenType.Comment) {
            this.current++
        }
    }

    private fun requireToken(requiredTokenType: TokenType, lazyMessage: () -> Unit): Token {
        this.skipComments()
        check(!this.isAtEnd()) {
            "Can't require anything beyond the token stream"
        }

        val token = this.peek()

        if (token.tokenType != requiredTokenType) {
            throw UnexpectedTokenException(
                token,
                "at ${this.currentPosition}: ${lazyMessage()}"
            )
        }
        this.advance()

        return token
    }

    private fun peek(offset: Int = 0): Token {
        val position = this.current + offset

        check(position >= 0) {
            "Can't peek before the token stream"
        }
        check(position < this.tokens.size) {
            "Can't peek beyond the token stream"
        }

        return this.tokens[position]
    }

    private fun isAtEnd(): Boolean {
        return this.tokens[this.current].tokenType == TokenType.EOF || this.current >= this.tokens.size
    }

    private fun advance(): Token {
        this.skipComments()
        require(!this.isAtEnd()) {
            "Can't advance past the token stream"
        }

        val token = this.peek()

        this.current++

        return token
    }

    private fun check(tokenType: TokenType): Boolean {
        return this.peek().tokenType != TokenType.EOF && this.peek().tokenType == tokenType
    }

    private fun check(tokenTypes: Set<TokenType>): Boolean {
        if (this.isAtEnd()) {
            return false
        }

        return this.peek().tokenType in tokenTypes
    }

    fun parse(): Ast.Statement.Select {
        return this.parseSelectStatement()
    }

    private fun parseSelectStatement(): Ast.Statement.Select {
        val select = this.parseSelectQuery()

        if (!this.isAtEnd()) {
            requireToken(TokenType.Semicolon) {
                "Expected ';' at the end of a statement"
            }
        }

        return Ast.Statement.Select(select)
    }

    private fun parseSelectQuery(): Ast.Expression.Select {
        val select = this.requireToken(TokenType.Select) {
            "Expected 'select' keyword at the start of select query"
        }

        var starSelect = false
        var results: List<Ast.Expression> = mutableListOf()
        if (this.check(TokenType.Star)) {
            starSelect = true
            this.requireToken(TokenType.Star) {
                "Expected '*' in star selection"
            }
        } else {
            results = this.parseSubstatementExpressionList(true)
        }

        if (!starSelect && results.isEmpty()) {
            throw ParseException("Results clause can't be empty")
        }

        if (!this.check(TokenType.From)) {
            if (starSelect) {
                throw ParseException("Can't select '*' without 'from' clause")
            }

            return Ast.Expression.Select(
                select,
                starSelect,
                results
            )
        }

        val from = this.parseFromClause()
        val where = if (this.check(TokenType.Where)) {
            this.parseConditionClause(TokenType.Where)
        } else {
            Ast.Expression.Literal(select,true)
        }

        val groupBy = if (this.check(TokenType.Group)) {
            this.advance()
            this.requireToken(TokenType.By) {
                "Expected 'by' keyword after 'group'"
            }
            this.parseSubstatementExpressionList(false)
        } else {
            listOf()
        }

        var having: Ast.Expression? = null
        if (groupBy.isNotEmpty()) {
            having = if (this.check(TokenType.Having)) {
                this.parseConditionClause(TokenType.Having)
            } else {
                Ast.Expression.Literal(this.peek(), true)
            }
        }

        val orderBy = if (this.check(TokenType.Order)) {
            this.advance()
            this.requireToken(TokenType.By) {
                "Expected 'by' keyword after 'order'"
            }
            this.parseOrderByList()
        } else {
            listOf()
        }

        var limit: Ast.Expression? = null
        var offset: Ast.Expression? = null

        if (this.check(TokenType.Limit)) {
            this.advance()
            limit = this.parseExpression()
        }

        if (this.check(TokenType.Offset)) {
            this.advance()
            offset = this.parseExpression()
        }

        return Ast.Expression.Select(
            select,
            starSelect,
            results,
            from.first,
            from.second,
            where,
            groupBy,
            having,
            orderBy,
            limit,
            offset
        )
    }

    private fun parseOrderByList(): List<OrderBy> {
        val sortColumns = mutableListOf<OrderBy>()

        while (true) {
            val identifier = this.parseIdentifier()
            val sortDirection = if (this.check(setOf(TokenType.Asc, TokenType.Desc))) {
                val direction = this.advance()
                sortDirectionFromTokenType(direction.tokenType)
            } else {
                SortDirection.Ascending
            }

            sortColumns.add(OrderBy(
                identifier,
                sortDirection
            ))

            if (!this.check(TokenType.Comma)) {
                break
            }
            this.requireToken(TokenType.Comma) {
                "Comma is expected in order by sorting list"
            }
        }

        return sortColumns
    }

    private fun parseFromClause():
        Pair<Ast.Expression.TableReference, List<Ast.Expression.JoinClause>>
    {
        this.requireToken(TokenType.From) {
            "Expected 'from' keyword at the start of from clause"
        }
        val joinTokens = setOf(
            TokenType.Left, TokenType.Inner,
            TokenType.Cross, TokenType.Comma
        )
        val tableReference = this.parseTableReference()
        val joins = mutableListOf<Ast.Expression.JoinClause>()

        do {
            if (!this.check(joinTokens)) (
                break
            )
            var joinType = this.advance()

            if (joinType.tokenType == TokenType.Comma) {
                // well . . .
                joinType = joinType.copy(tokenType = TokenType.Cross)
            } else {
                this.requireToken(TokenType.Join) {
                    "Expected 'join' keyword after ${joinType.tokenType.name}"
                }
            }

            val joinTableReference = this.parseTableReference()

            val joinCondition = if (joinType.tokenType != TokenType.Cross && this.check(TokenType.On)) {
                this.advance()
                this.parseExpression()
            } else {
                null
            }
            joins.add(Ast.Expression.JoinClause(
                joinType,
                joinTableReference,
                joinCondition
            ))
        } while (!this.check(setOf(
            TokenType.Where, TokenType.Group, TokenType.Order,
        )))

        return Pair(
            tableReference,
            joins
        )
    }

    private fun parseTableReference(): Ast.Expression.TableReference {
        var table = this.parseExpression()

        if (table is Ast.Expression.Grouping) {
            table = table.expression
        }

        if ((table !is Ast.Expression.Selectable) && (table !is Ast.Expression.Identifier)) {
            throw ParseException(
                "at ${this.currentPosition}" +
                        "Expected table identifier or subquery as table reference"
            )
        }

        val alias = this.parseOptionalAlias()
        if (table is Ast.Expression.Identifier) {
            table = Ast.Expression.TableIdentifier(
                table.name
            )
        }
        val reference = Ast.Expression.TableReference(
            this.peek(),
            table as Ast.Expression.Selectable,
        )
        reference.alias = alias?.name

        return reference
    }

    private fun parseOptionalAlias(): Ast.Expression.Identifier? {
        if (!this.check(TokenType.As)) {
            return null
        }

        this.advance()
        val identifier = this.parseIdentifier()

        return identifier
    }

    private fun parseConditionClause(tokenType: TokenType): Ast.Expression {
        this.requireToken(tokenType) {
            "Expected '${tokenType.name}' keyword at the star of ${tokenType.name} clause"
        }

        return this.parseExpression()
    }

    private fun<Expr> parseLeftAssociative(
        operators: Set<TokenType>,
        parseOperand: Parser.() -> Expr,
        buildExpression: (Expr, Token, Expr) -> Expr
    ): Expr {
        var expression = parseOperand()

        while (this.check(operators)) {
            val operator = this.advance()
            val right = parseOperand()
            expression = buildExpression(expression, operator, right)
        }

        return expression
    }

    private fun parseExistSubquery(): Ast.Expression {
        val exist = this.requireToken(TokenType.Exists) {
            "Expected 'exists' keyword at the start of exist subquery"
        }
        this.requireToken(TokenType.LeftParenthesis) {
            "Expected '(' after exist keyword"
        }

        val existSubquery = this.parseSelectQuery()
        this.requireToken(TokenType.RightParenthesis) {
            "Expected ')' after exist subquery"
        }

        return Ast.Expression.ExistSubquery(
            exist,
            existSubquery
        )
    }

    private fun parseOrExpression(): Ast.Expression {
        return this.parseLeftAssociative(
            setOf(TokenType.Or),
            Parser::parseAndExpression
        ) {
            lhs, op, rhs -> Ast.Expression.Logical(lhs, op, rhs)
        }
    }

    private fun parseAndExpression(): Ast.Expression {
        return this.parseLeftAssociative(
            setOf(
                TokenType.And
            ),
            Parser::parseEqualityExpression
        ) {
            lhs, op, rhs -> Ast.Expression.Logical(lhs, op, rhs)
        }
    }

    private fun parseEqualityExpression(): Ast.Expression {
        return this.parseLeftAssociative(
            setOf(
                TokenType.Equals, TokenType.NotEquals,
            ),
            Parser::parseComparisonExpression
        ) {
            lhs, op, rhs -> Ast.Expression.Binary(lhs, op, rhs)
        }
    }

    private fun parseComparisonExpression(): Ast.Expression {
        return this.parseLeftAssociative(
            setOf(
                TokenType.Less, TokenType.LessEquals,
                TokenType.Greater, TokenType.GreaterEquals,
            ),
            Parser::parseAdditiveExpression
        ) {
            lhs, op, rhs -> Ast.Expression.Binary(lhs, op, rhs)
        }
    }

    private fun parseAdditiveExpression(): Ast.Expression {
        return this.parseLeftAssociative(
            setOf(
                TokenType.Plus, TokenType.Minus,
            ),
            Parser::parseMultiplicativeExpression
        ) {
            lhs, op, rhs -> Ast.Expression.Binary(lhs, op, rhs)
        }
    }

    private fun parseMultiplicativeExpression(): Ast.Expression {
        return this.parseLeftAssociative(
            setOf(
                TokenType.Star, TokenType.Slash,
            ),
            Parser::parseUnaryExpression
        ) {
            lhs, op, rhs -> Ast.Expression.Binary(lhs, op, rhs)
        }
    }

    private fun parseUnaryExpression(): Ast.Expression {
        if (this.check(setOf(TokenType.Not, TokenType.Minus))) {
            val operator = this.advance()
            val right = this.parsePostfixExpression()

            return Ast.Expression.Unary(
                operator,
                right
            )
        }

        return this.parsePostfixExpression()
    }

    private fun parsePostfixExpression(): Ast.Expression {
        val call = this.parseFunctionCall()

        var not: Token? = null
        var operator = this.peek()

        if (operator.tokenType == TokenType.Not) {
            not = this.advance()
            operator = this.peek()
        }

        val expr = when (operator.tokenType) {
            TokenType.In -> this.parseInExpression(call)
            TokenType.Between -> this.parseBetweenExpression(call)
            TokenType.Like -> this.parseLikeExpression(call)
            else -> call
        }

        if (not != null) {
            return Ast.Expression.Unary(
                not,
                expr
            )
        }

        return expr
    }

    private fun parseInExpression(left: Ast.Expression): Ast.Expression {
        val inToken = this.requireToken(TokenType.In) {
            "Expected 'in' keyword"
        }

        val range = this.parseExpressionList()

        check(range.isNotEmpty()) {
            "'in' expression range can't be empty"
        }

        return Ast.Expression.In(
            inToken,
            left,
            range
        )
    }

    private fun parseLikeExpression(left: Ast.Expression): Ast.Expression {
        val like = this.requireToken(TokenType.Like) {
            "Expected 'like' keyword "
        }

        val right = this.parsePrimaryExpression()

        return Ast.Expression.Like(
            like,
            left,
            right
        )
    }

    private fun parseBetweenExpression(left: Ast.Expression): Ast.Expression {
        val between = this.requireToken(TokenType.Between) {
            "Expected 'between' keyword at the start of between expression"
        }

        val first = this.parsePrimaryExpression()
        this.requireToken(TokenType.And) {
            "Expected 'and' keyword after first between argument"
        }

        val second = this.parsePrimaryExpression()

        return Ast.Expression.Between(
            between,
            left,
            first,
            second
        )
    }

    private fun parseIdentifier(): Ast.Expression.Identifier {
        val identifier = this.requireToken(TokenType.Identifier) {
            "Expected identifier"
        }

        if (this.check(TokenType.Dot)) {
            this.advance()
            val columnName = this.requireToken(TokenType.Identifier) {
                "Expected identififer after '.'"
            }

            return Ast.Expression.Identifier(
                columnName,
                identifier
            )
        }

        return Ast.Expression.Identifier(identifier)
    }

    private fun parseFunctionCall(): Ast.Expression {
        var expr = this.parsePrimaryExpression()

        while (true) {
            if (this.check(TokenType.LeftParenthesis)) {
                val leftParent = this.advance()
                val arguments = mutableListOf<Ast.Expression>()
                var starArgument = false

                if (!this.check(TokenType.RightParenthesis)) {
                    if (this.check(TokenType.Star)) {
                        this.advance()
                        starArgument = true
                    } else {
                        while (true) {
                            val arg = this.parseExpression()
                            arguments.add(arg)

                            if (this.check(TokenType.RightParenthesis)) {
                                break
                            } else if (this.check(TokenType.Comma)) {
                                this.advance()
                            }
                        }
                    }
                }

                val paren = this.requireToken(TokenType.RightParenthesis) {
                    "Expected ')' after the call ${leftParent.line}"
                }

                if (starArgument && arguments.isNotEmpty()) {
                    throw ParseException(
                        "at ${this.currentPosition}: Can't have * and arguments list"
                        + " in a function call"
                    )
                }
                expr = Ast.Expression.Call(
                    expr,
                    paren,
                    arguments,
                    starArgument
                )
            } else {
                break
            }
        }

        return expr
    }

    private fun parsePrimaryExpression(): Ast.Expression {
        val token = this.peek()

        return when (token.tokenType) {
            TokenType.LeftParenthesis -> {
                val nextToken = this.peek()
                if (nextToken.tokenType == TokenType.Select) {
                    this.parseSelectQuery()
                } else {
                    this.parseGroupingExpression()
                }
            }
            TokenType.Select ->
                this.parseSelectQuery()
            TokenType.Exists ->
                this.parseExistSubquery()
            TokenType.Case ->
                this.parseCaseExpression()
            TokenType.Identifier ->
                this.parseIdentifier()
            TokenType.Not, TokenType.Minus ->
                this.parseUnaryExpression()
            TokenType.Number, TokenType.String ->
                this.parseLiteral(token.literal)
            TokenType.True ->
                this.parseLiteral(true)
            TokenType.False ->
                this.parseLiteral(false)
            TokenType.Null ->
                this.parseLiteral(null)
            else ->
                throw ParseException(
                    "Expected expression at ${this.currentPosition}" +
                    " but got $token"
                )
        }
    }

    private fun parseCaseExpression(): Ast.Expression.Case {
        val case = this.requireToken(TokenType.Case) {
            "Expected 'case' keyword at the start of case expression"
        }

        val whenExpressions = mutableListOf<Ast.Expression>()
        val thenExpressions = mutableListOf<Ast.Expression>()

        while (!this.check(TokenType.Else)) {
            this.requireToken(TokenType.When) {
                "Expected 'when' keyword at the branch of case expression"
            }

            val expression = this.parseExpression()
            whenExpressions.add(expression)

            this.requireToken(TokenType.Then) {
                "Expected 'then' keyword at the end of the branch of case expression"
            }

            val then = this.parseExpression()
            thenExpressions.add(then)
        }

        this.requireToken(TokenType.Else) {
            "Expected 'else' keyword at the last branch of case expression"
        }
        val elseBranch = this.parseExpression()

        this.requireToken(TokenType.End) {
            "Expected 'end' keyword at the end of case expression"
        }

        return Ast.Expression.Case(
            case,
            whenExpressions,
            thenExpressions,
            elseBranch
        )
    }

    private fun parseGroupingExpression(): Ast.Expression {
        this.requireToken(TokenType.LeftParenthesis) {
            "Expected '(' at the start of the grouping"
        }

        val expression = this.parseExpression()

        this.requireToken(TokenType.RightParenthesis) {
            "Expected ')' at the end of the grouping"
        }

        return Ast.Expression.Grouping(expression)
    }

    private fun parseLiteral(value: Any?): Ast.Expression.Literal {
        val token = this.advance()

        return Ast.Expression.Literal(
            token,
            value
        )
    }

    private fun parseExpressionList(): List<Ast.Expression> {
        this.requireToken(TokenType.LeftParenthesis) {
            "Required '(' at the start of expression list"
        }

        val expressions = mutableListOf<Ast.Expression>()

        while (!this.check(TokenType.RightParenthesis)) {
            val expression = this.parseExpression()
            expressions.add(expression)

            // what the
            if (!this.check(TokenType.RightParenthesis)) {
                if (this.isAtEnd()) {
                    break
                }
                this.requireToken(TokenType.Comma) {
                    "Comma required after expression in expression list"
                }
            }
        }

        if (expressions.isEmpty()) {
            throw ParseException(
                "at: ${this.currentPosition} " +
                        "Expression list can't be empty"
            )
        }

        this.requireToken(TokenType.RightParenthesis) {
            "Required ')' at the end of expression list"
        }

        return expressions
    }

    private fun parseSubstatementExpressionList(aliased: Boolean = true): List<Ast.Expression> {
        val expressions = mutableListOf<Ast.Expression>()

        while (true) {
            val expression = this.parseExpression()

            if (aliased) {
                val alias = this.parseOptionalAlias()
                expression.alias = alias?.name
            }

            expressions.add(expression)
            if (!this.check(TokenType.Comma)) {
                break
            }

            this.requireToken(TokenType.Comma) {
                "Expected comma after expression in expression list"
            }
        }

        return expressions
    }

    private fun parseExpression(): Ast.Expression {
        return this.parseOrExpression()
    }
}
