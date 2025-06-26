import dataselector.Interpreter
import dataselector.interpreter.Column
import dataselector.interpreter.Row
import dataselector.interpreter.Selectable
import dataselector.interpreter.Table
import parser.Parser
import scanner.Scanner
import scanner.TokenType
import syntax.EcwidSelectView
import java.io.File


class TestCollectionEntry(
    var field1: Double
) : Selectable {
    override fun asRow(): Row {
//        return Row(mapOf(
//            "test_collection.field1" to this.field1
//        ))
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
//        return Row(mapOf(
//            "field1" to this.field1,
//            "field2" to this.field2,
//        ))
    }

    override fun fromRow(row: Row) {
        this.field1 = (row.get("field1", "filter_collection")!! as Double)
        this.field2 = (row.get("filed2", "filter_collection")!! as String)
    }
}

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val text = File("src/tests/testsql.sql").readText().lowercase()
    val scanner = Scanner(text)

    val tokens = scanner.scanTokens().filter {
        token -> token.tokenType != TokenType.Comment
    }.toList()
    tokens.forEach {
        token -> println(token)
    }

    val parser = Parser(tokens)

    val parseResult = parser.parse()
//    println(parseResult)
    val ecwidView = EcwidSelectView.Factory.fromAst(
        parseResult
    )
    println(ecwidView.prettyPrint(maxDepth = 10))
//    val interpreter = Interpreter(ExecutionContext(mutableMapOf()))

    val collection = listOf(
        TestCollectionEntry(1.0),
//        TestCollectionEntry(2.0),
//        TestCollectionEntry(3.0),
//        TestCollectionEntry(4.0),
//        TestCollectionEntry(5.0),
        TestCollectionEntry(6.0),
        TestCollectionEntry(7.0),
        TestCollectionEntry(7.0),
        TestCollectionEntry(8.0),
//        TestCollectionEntry(9.0),
//        TestCollectionEntry(10.0),
//        TestCollectionEntry(11.0),
//        TestCollectionEntry(12.0),
//        TestCollectionEntry(13.0),
//        TestCollectionEntry(14.0),
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

    val value = interpreter.execute(parseResult).toList()

    value.forEach {
        v -> println(v)
    }
}