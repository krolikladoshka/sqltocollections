import dataselector.Interpreter
import dataselector.interpreter.Column
import dataselector.interpreter.Row
import dataselector.interpreter.Selectable
import dataselector.interpreter.Table
import parser.Parser
import scanner.Scanner
import scanner.TokenType
import syntax.EcwidSelectView
import syntax.Source
import java.io.File


class TestCollectionEntry(
    var field1: Double
) : Selectable {
    override fun asRow(): Row {
        return Row(
            "test_collection",
            listOf(
                Column("test_collection", "field1", "field1", 0),
            ),
            listOf(
                this.field1
            )
        )
    }

    override fun fromRow(row: Row) {
        this.field1 = (row.get("field1", "test_collection")!! as Double)
    }
}

class TestCollectionEntry2(
    var field1: Double,
    var field2: String
) : Selectable {
    override fun asRow(): Row {
        return Row(
            "filter_collection",
            listOf(
                Column("filter_collection", "field1", "field1", 0),
                Column("filter_collection", "field2", "field2", 1),
            ),
            listOf(
                this.field1,
                this.field2
            )
        )
    }

    override fun fromRow(row: Row) {
        this.field1 = (row.get("field1", "filter_collection")!! as Double)
        this.field2 = (row.get("filed2", "filter_collection")!! as String)
    }
}

fun formatRow(row: List<String>, widths: List<Int>): String {
    return buildString {
        append('|')
        row.forEachIndexed {
            i, value ->
                append(value.padStart(widths[i]))
                append('|')
        }
    }
}

    fun tablePrint(table: Table) {
    val data: List<List<String>> = table.map {
        row -> row.asEntries().map {
            entry -> entry.second?.toString() ?: "null"
        }
    }.toList()

    val fields = table.first().asEntries().map {
        it.first
    }

    val widths: List<Int> = fields.map {
        it.length
    }
    val sepLine = "-".repeat(widths.sum() + widths.size * 2)

    println(sepLine)
    println(formatRow(fields, widths))
    println(sepLine)

    data.forEach {
        println(formatRow(it, widths))
    }

    println(sepLine)
}

fun main() {
    val text = File(
        "test/data/sql/complex_nested_sql_with_all_lang_statements.sql"
    ).readText()
    println("Parsing\n${text}")
    val scanner = Scanner(text)

    val tokens = scanner.scanTokens().filter {
        token -> token.tokenType != TokenType.Comment
    }.toList()

    val parser = Parser(tokens)
    val parseResult = parser.parse()
    val ecwidView = EcwidSelectView.Factory.fromAst(
        parseResult
    )
    println()
    println("#".repeat(20))
    println()
    println("Query's shallow viewAST\n${ecwidView}")
    println()
    println("#".repeat(20))
    println()
    println("Query's full viewAST")
    println("#".repeat(20))
    println(ecwidView.prettyPrint(maxDepth = 10))
    println()
    println("#".repeat(20))
    println()

    ecwidView.fromSource!!.let {
        println("Example: joins")

        val inner = it as Source.SubqueryTable
        inner.select.joins.forEachIndexed {
            index, join ->
                println("$index:")
                println(join)
        }
        println("Example: split having's condition expression")
        print("Operands: ")
        print(inner.select.havingClauses.operands.joinToString(", ") {
            it.toString()
        })
        println()
        print("Operators: ")
        inner.select.havingClauses.operators.forEach {
            print(it)
        }
        println()
    }

    println("#".repeat(20))
    println("#".repeat(20))
    println("#".repeat(20))
    println("Buggy interpretation of the query (scope resolving issues):")
    val collection = listOf(
        TestCollectionEntry(1.0),
        TestCollectionEntry(6.0),
        TestCollectionEntry(7.0),
        TestCollectionEntry(7.0),
        TestCollectionEntry(8.0),
    )
    val filterCollection = listOf(
        TestCollectionEntry2(6.0, "test1"),
        TestCollectionEntry2(7.0, "Test2"),
        TestCollectionEntry2(8.0, "Test3"),
        TestCollectionEntry2(9.0, "Test4")
    )
    val bindings: MutableMap<String, Table> = mutableMapOf(
        "test_collection" to Table(
            "test_collection",
        listOf("field1"),
        collection.map {
                value -> value.asRow()
            }.toList()
        ),
        "filter_collection" to Table(
            "filter_collection",
            listOf("field1", "field2"),
            filterCollection.map {
                value -> value.asRow()
            }.toList()
        )
    )

    val interpreter = Interpreter(bindings)

    val value = interpreter.execute(parseResult)

    tablePrint(value)

    println("Done")
}