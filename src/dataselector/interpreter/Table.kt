package dataselector.interpreter


interface Selectable {
    fun asRow(): Row

    fun fromRow(row: Row)
}

data class Table(
    var name: String,
    val fields: List<String>,
    val rows: List<Row>,
    var scope: Scope? = null,
) : List<Row> by rows {
    fun mapRows(transform: List<Row>.() -> List<Row>): Table {
        return Table(this.name,this.fields, transform(this))
    }

    fun setTableName(name: String) {
        this.name = name
        this.rows.forEach {
                r ->
            r.tableAliases.clear()
            r.tableAliases[name] = 0
            r.columns = r.columns.map {
                    c -> c.copy(tableAlias = name)
            }.toMutableList()
        }
    }
}

fun Table.innerJoin(right: Table, on: (Row, Row) -> Boolean): Table {
    val joined = this.flatMap {
            leftRow ->
        val result = right.filter {
                rightRow ->
            val result = on(leftRow, rightRow)
            result
        }.map {
                rightRow -> leftRow.join(
            rightRow, this@innerJoin.name, right.name
        )
        }.toList()

        result
    }

    return Table(
        "${this.name}_${right.name}_inner_joined",
        this.fields + right.fields, joined
    )
}

fun Table.leftJoin(right: Table, on: (Row, Row) -> Boolean): Table {
    val joined = this.rows.flatMap {
            leftRow ->
        val matches = right.rows.filter {
                rightRow -> on(leftRow, rightRow)
        }.map {
                rightRow ->  leftRow.join(
            rightRow, this@leftJoin.name, right.name
        )
        }.toList()

        if (matches.isEmpty()) {
            sequenceOf(leftRow + Row(
                right.name, right.rows.first().columns, listOf())
            )
        } else {
            matches.asSequence()
        }
    }

    return Table(
        "${this.name}_${right.name}_left_joined",
        this.fields + right.fields, joined
    )
}

fun Table.crossJoin(right: Table): Table {
    val joined = this.rows.flatMap {
        leftRow -> right.rows.map {
            rightRow ->  leftRow.join(
        rightRow, this@crossJoin.name, right.name
            )
        }
    }

    return Table(
        "${this.name}_${right.name}_cross_joined",
        this.fields + right.fields, joined,
    )
}
