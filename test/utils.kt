import dataselector.Interpreter
import dataselector.interpreter.Column
import dataselector.interpreter.Row
import dataselector.interpreter.Selectable
import dataselector.interpreter.Table
import org.junit.jupiter.api.Assertions.assertEquals
import parser.Parser
import scanner.Scanner
import java.io.File
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

fun loadFile(fileName: String): String {
    val file = File("test/data", fileName)
    require(file.exists()) {
        "File $file is not found"
    }

    return file.readText(StandardCharsets.UTF_8)
}



fun getMemoryDb(): Connection {
    return DriverManager.getConnection(
        "jdbc:sqlite::memory:"
    ).apply {
        autoCommit = true
    }
}


fun executeSqlScript(conn: Connection, filePath: String) {
    val sql = loadFile(filePath)

    conn.createStatement().use {
        stmt ->
            sql.split(';')
            .map(String::trim)
            .filter {
                it.isNotEmpty()
            }
            .forEach {
                query ->
                try {
                    stmt.execute(query)
                } catch (e: SQLException) {
                    throw SQLException(
                        """
                        Failed to execute SQL statement:
                        $query
                        ${e.message}
                        """.trimIndent(),
                        e.sqlState,
                        e.errorCode,
                        e
                    )
                }
            }
    }
}


fun queryAll(conn: Connection, sql: String): Pair<List<Pair<String, String>>, List<List<Any?>>> {
    return conn.createStatement().use {
        stmt ->
            try {
                stmt.executeQuery(sql).use {
                        rs ->
                    val cols = rs.metaData.columnCount
                    val columns = (1..cols).map {
                        Pair(
                            rs.metaData.getColumnName(it),
                            rs.metaData.getColumnLabel(it)
                        )
                    }
                    val rows = mutableListOf<List<Any?>>()

                    while (rs.next()) {
                        rows += (1..cols).map {
                            rs.getObject(it)
                        }
                    }

                    Pair(columns, rows)
                }
            } catch (e: SQLException) {
                throw SQLException(
                    """
                        Failed to execute SQL statement:
                        $sql
                        SQLite error [sqlState=${e.sqlState}, errorCode=${e.errorCode}]:
                        ${e.message}
                        """.trimIndent(),
                    e.sqlState,
                    e.errorCode,
                    e
                )
            }
    }
}

fun withMemoryDb(scriptFilePath: String, block: (List<Pair<String, String>>, List<List<Any?>>) -> Unit) {
    val db = getMemoryDb()
    val sql = loadFile(scriptFilePath)
    executeSqlScript(db, "sql/init.sql")
    val actualResults = queryAll(db, sql)
    block(actualResults.first, actualResults.second)
}


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

class FilterCollectionEntry(
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

fun parseAndExecuteSql(sql: String): Table {
    val scanner = Scanner(sql)
    val tokens = scanner.scanTokens()

    val parser = Parser(tokens.toList())
    val statement = parser.parse()

    val collection = listOf(
        TestCollectionEntry(1.0),
        TestCollectionEntry(6.0),
        TestCollectionEntry(7.0),
        TestCollectionEntry(7.0),
        TestCollectionEntry(8.0),
    )
    val filterCollection = listOf(
        FilterCollectionEntry(6.0, "test1"),
        FilterCollectionEntry(7.0, "Test2"),
        FilterCollectionEntry(8.0, "Test3"),
        FilterCollectionEntry(9.0, "Test4")
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

    return interpreter.execute(statement)
}




fun assertRowsEqualToDb(
    table: Table, expectedColumns: List<Pair<String, String>>, expectedValues: List<List<Any?>>
) {
    table.rows.zip(expectedValues).forEach {
        val row = it.first
        val expectedRow = it.second
        assertEquals(expectedColumns.size, row.columns.size)

        row.columns.zip(expectedColumns).forEach {
            actualExpected->
                val actual = actualExpected.first
                val expected = actualExpected.second
                assertEquals(expected.second, actual.alias)
        }
        assertEquals(expectedRow, row.values)
    }
}


fun assertAgainstMemoryDb(scriptFilePath: String) {
    val sql = loadFile(scriptFilePath)
    val resultRows = parseAndExecuteSql(sql)

    withMemoryDb(scriptFilePath) {
        columns, values ->
            assertRowsEqualToDb(resultRows, columns, values)
    }
}