package scanner

data class Token(
    val tokenType: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
    val column: Int
) {
    override fun toString(): String {
        return "${this.tokenType.name}:${this.lexeme}:${this.literal} ${this.at()}"
    }

    fun at(): String {
        return "at ${this.line}:${this.column}"
    }
}