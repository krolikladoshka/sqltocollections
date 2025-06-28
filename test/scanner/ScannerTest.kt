package scanner

import loadFile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScannerTest {
    private fun scan(source: String): List<Token> {
        return Scanner(source).scanTokens().toList()
    }

    private fun scanFromFile(filePath: String): List<Token> {
        val text = loadFile(filePath)

        return this.scan(text)
    }
    @Test
    fun `recognises all terminals`() {
        val tokens = scanFromFile("terminals.txt")
        assertEquals(58, tokens.size)
    }

    @Test
    fun `correctly calculates line & column`() {
        val tokens = scanFromFile("terminals.txt")

        for (i in 0 until tokens.size) {
            println(tokens[i])
            assertEquals(i + 1, tokens[i].line)
            assertEquals(1, tokens[i].column    )
        }
    }
}