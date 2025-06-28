package dataselector.interpreter

data class Column(
    val tableAlias: String,
    val alias: String,
    val name: String,
    val index: Int,
)


class Row {
    val tableAliases: MutableMap<String, Int>
    var columns: MutableList<Column>
    val values: List<Any?>

    constructor(tableName: String, columns: List<Column>, values: List<Any?>) {
        require(columns.size == values.size) {
            "Columns count should match values count for row: $columns"
        }
        this.columns = columns.toMutableList()
        this.values = values
        this.tableAliases = mutableMapOf(
            tableName to 0
        )
    }

    private fun getColumnBy(name: String, tableAlias: String, predicate: (Column) -> Boolean): Column? {
        val columns = this.columns.filter {
            c -> c.tableAlias == tableAlias
        }.filter(predicate).take(2).toList()

        if (columns.size > 1) {
            throw ExecutionException("Ambiguous column $name")
        } else if (columns.size == 1) {
            return columns[0]
        }

        return null
    }

    fun getColumnByName(name: String, tableAlias: String): Column? {
        return this.getColumnBy(name, tableAlias) {
            c -> c.name == name
        }
    }

    fun getColumnByAlias(alias: String, tableAlias: String): Column? {
        return this.getColumnBy(alias, tableAlias) {
            c -> c.alias == alias
        }
    }

    fun getColumn(name: String, tableAlias: String): Column? {
        val column = this.getColumnByAlias(name, tableAlias)

        if (column != null) {
            return column
        }

        return this.getColumnByName(name, tableAlias)
    }

    fun getByName(name: String, tableAlias: String): Any? {
        val column = this.getColumnByName(name, tableAlias)

        if (column == null) {
            return null
        }

        return this.values[column.index]
    }

    fun getByAlias(alias: String, tableAlias: String): Any? {
        val column = this.getColumnByAlias(alias, tableAlias)

        if (column == null) {
            return null
        }

        return this.values[column.index]
    }

    fun get(name: String, tableAlias: String): Any? {
        val column = this.getColumn(name, tableAlias)

        if (column == null) {
            return null
        }

        try {
            return this.values[column.index]
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw e
        }
    }

    fun join(right: Row, leftTableName: String, rightTableName: String): Row {
        val columns = this.columns.toMutableList()
        val leftColumnsSize = columns.size
        right.columns.forEach {
            c -> columns.add(c.copy(index = leftColumnsSize + c.index))
        }
        val values = this.values + right.values

        val row = Row("${leftTableName}_$rightTableName", columns, values)
        row.tableAliases[rightTableName] = leftColumnsSize

        return row
    }

    fun asEntries(): List<Pair<String, Any?>> {
        return this.columns.map {
            column ->
                Pair(column.alias, this@Row.values[column.index])
        }
    }

    operator fun plus(right: Row): Row {
        return this.join(right, "left", "right")
    }

    override fun toString(): String {
        val cv = this.columns.zip(this.values).joinToString(";", prefix = "Row: ") {
            cv -> "${cv.first} = ${cv.second}"
        }
        return cv
    }
}