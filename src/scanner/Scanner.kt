package scanner

fun Char.isAsciiAlphabetic(): Boolean {
    return (this in 'A'..'Z') || (this in 'a'..'z')
}

fun Char.isAsciiDigit(): Boolean {
    return this in '0'..'9'
}

fun Char.isAsciiAlphanumeric(): Boolean {
    return this.isAsciiAlphabetic() || this.isAsciiDigit()
}


fun Char.canFormIdentifier(): Boolean {
    return this == '_' || this.isAsciiAlphanumeric()
}

fun Char.canStartIdentifier(): Boolean {
    return this == '_' || this.isAsciiAlphabetic()
}

open class ScannerException(message: String) : RuntimeException(message)

class UnexpectedCharacterException(
    character: Char,
    message: String
) : ScannerException("$character $message")


class Scanner(
    private val source: String,
    private val tokens: MutableList<Token> = mutableListOf(),
    private var start: Int = 0,
    private var current: Int = 0,
    private var currentLine: Int = 1,
    private var currentColumn: Int = 1,
    private var line: Int = 1,
    private var column: Int = 1,
) {
    private fun newLine() {
        this.currentLine++
        this.currentColumn = 1
    }

    private fun getUpToCursor(startOffset: Int = 0): String {
        require(startOffset >= 0) {
            "Can't offset start backwards"
        }
        require(this.start < this.source.length) {
            "Can't slice a token from ended stream"
        }
        val current = if (this.isAtEnd()) {
            this.current - 1
        } else {
            this.current
        }
        require(current <= this.source.length) {
            "Pointer advanced past the source stream"
        }

        val start = this.start + startOffset

        require(start <= current) {
            "Start can't be farther than current cursor"
        }

        return this.source.substring(start, current)
    }

    private fun isAtEnd(): Boolean {
        return this.current >= this.source.length
    }

    private fun peek(offset: Int = 0): Char {
        val position = this.current + offset

        check(position >= 0) {
            "Can't look past the source stream"
        }

        if (position >= this.source.length) {
            return '\u0000'
        }

        return this.source[position]
    }

    private fun advance(): Char {
        assert(!isAtEnd()) {
            "Can't advance past source stream"
        }

        val c = this.peek()
        this.current++
        this.currentColumn++

        return c
    }

    private fun produceSimpleToken(tokenType: TokenType): Token {
        val token = this.makeToken(tokenType)
        this.advance()

        return token
    }

    private fun requireSymbol(symbol: Char, lazyMessage: () -> Unit): Char {
        if (this.peek() != symbol) {
            throw UnexpectedCharacterException(
                symbol,
                "at ${this.currentLine}:${this.currentColumn}: ${lazyMessage()}"
            )
        }

        return this.advance()
    }

    private fun scanDoubleToken(
        nextSymbol: Char, singleTokenType: TokenType, doubleTokenType: TokenType
    ): Token {
        if (this.peek(1) == nextSymbol) {
            repeat(2) {
                this.advance()
            }

            return this.makeToken(doubleTokenType)
        } else {
            this.advance()

            return this.makeToken(singleTokenType)
        }
    }

    private fun scanDoubleToken(
        singleTokenType: TokenType, nextTokenTypeMap: Map<Char, TokenType>
    ): Token {
        val c = this.peek(1)

        if (nextTokenTypeMap.containsKey(c)) {
            repeat(2) {
                this.advance()
            }

            return this.makeToken(nextTokenTypeMap[c]!!)
        } else {
            this.advance()
            return this.makeToken(singleTokenType)
        }
    }

    private fun scanComment(): Token {
        requireSymbol('-') {
            "Expected '-' as start of the token"
        }
        requireSymbol('-') {
            "Expected second '-' in comment token"
        }

        while (this.peek() != '\n' && !this.isAtEnd()) {
            this.advance()
        }

        if (!this.isAtEnd()) {
            this.advance()
            this.newLine()
        }

        return this.makeToken(
            TokenType.Comment,
            this.getUpToCursor(2),
            this.line,
            this.column
        )
    }

    private fun getNumber(): String {
        while (this.peek().isAsciiDigit()) {
            this.advance()
        }

        return this.getUpToCursor()
    }

    private fun scanNumber(): Token {
        var numberStr = this.getNumber()

        if (this.peek() == '.' && this.peek(1).isAsciiDigit()) {
            this.advance()
            numberStr = this.getNumber()
        }

        val number = try {
            numberStr.toDouble()
        } catch (e: NumberFormatException) {
            throw e
        }

        return this.makeToken(
            TokenType.Number,
            number
        )
    }

    private fun scanString(): Token {
        requireSymbol('\'') {
            "Expected \"'\" at the start of the string"
        }

        var stringEnd: Int
        while (true) {
            val c = this.advance()

             if (c == '\'') {
                stringEnd = this.current
                this.advance()
                break
            }

            if (this.isAtEnd() || c == '\n') {
                throw ScannerException(
                    "Unterminated string literal at ${this.currentLine}:${this.currentColumn}"
                )
            }
        }

        val stringValue = this.source.substring(this.start + 1, stringEnd - 1)

        return this.makeToken(
            TokenType.String,
            stringValue
        )
    }

    private fun scanIdentifier(): Token {
        while (this.peek().canFormIdentifier()) {
            this.advance()
        }

        val identifier = this.getUpToCursor()

        val tokenType = keywords.getOrDefault(
            identifier, TokenType.Identifier
        )

        return this.makeToken(
            tokenType,
            identifier
        )
    }

    private fun skipWhitespace() {
        while (!this.isAtEnd()) {
            val c = this.peek()

            if (c.isWhitespace()) {
                if (c == '\n') {
                    this.newLine()
                }

                this.advance()
            } else {
                break
            }
        }
    }

    private fun scanToken(): Token {
        if (this.isAtEnd()) {
            return makeToken(TokenType.EOF)
        }

        var c = this.peek()

        if (c.isWhitespace()) {
            this.skipWhitespace()
            this.updateCursors()
            c = this.peek()
        }

        when (c) {
            '+' -> return this.produceSimpleToken(TokenType.Plus)
            '-' -> {
                if (this.peek(1) == '-') {
                    return this.scanComment()
                }
                return this.produceSimpleToken(TokenType.Minus)
            }
            '*' -> return this.produceSimpleToken(TokenType.Star)
            '/' -> return this.produceSimpleToken(TokenType.Slash)
            '%' -> return this.produceSimpleToken(TokenType.Percent)
            '=' -> return this.produceSimpleToken(TokenType.Equals)
            '.' -> return this.produceSimpleToken(TokenType.Dot)
            ',' -> return this.produceSimpleToken(TokenType.Comma)
            '<' -> return this.scanDoubleToken(
                TokenType.Less,
                mapOf(
                    '=' to TokenType.LessEquals, '>' to TokenType.NotEquals
                )
            )
            '>' -> return this.scanDoubleToken(
                '=',
                TokenType.Greater,
                TokenType.GreaterEquals
            )
            '\'' -> return this.scanString()
            '(' -> return this.produceSimpleToken(TokenType.LeftParenthesis)
            ')' -> return this.produceSimpleToken(TokenType.RightParenthesis)
            '[' -> return this.produceSimpleToken(TokenType.LeftSquareBracket)
            ']' -> return this.produceSimpleToken(TokenType.RightSquareBracket)
            ';' -> return this.produceSimpleToken(TokenType.Semicolon)
            else -> {
                return if (c.isAsciiDigit()) {
                    this.scanNumber()
                } else if (c.canStartIdentifier()) {
                    this.scanIdentifier()
                } else {
                    throw ScannerException(
                        "Unknown token $c at ${this.currentLine}:${this.currentColumn}"
                    )
                }
            }
        }
    }

    private fun makeToken(
        tokenType: TokenType, literal: Any? = null,
        customLine: Int = currentLine, customColumn: Int = column
    ): Token {
        val lexeme = if (tokenType == TokenType.EOF) {
            ""
        } else {
            this.getUpToCursor()
        }
        return Token(
            tokenType,
            lexeme,
            literal,
            customLine,
            customColumn
        )
    }

    fun updateCursors() {
        this.start = this.current
        this.line = this.currentLine
        this.column = this.currentColumn
    }

    fun scanTokens(): Sequence<Token> {
        return sequence {
            while (true) {
                this@Scanner.updateCursors()

                val token = this@Scanner.scanToken()
                this@Scanner.tokens.add(token)

                yield(token)

                if (token.tokenType == TokenType.EOF) {
                    break
                }
            }
        }
    }
}