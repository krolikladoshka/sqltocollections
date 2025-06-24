package dataselector.interpreter

import syntax.Ast

class ExecutionContext{
    var tables: MutableMap<String, Table>
    var tableAliases: MutableMap<String, String>  = mutableMapOf()

    constructor() {
        this.tables = mutableMapOf()
        this.tableAliases = mutableMapOf()
    }

    constructor(tables: Map<String, Table>) {
        this.tables = tables.toMutableMap()
        this.tableAliases = tables.flatMap {
            entry -> listOf(
                Pair(entry.key, entry.value.name),
                Pair(entry.value.name, entry.value.name)
            )
        }.toMap().toMutableMap()
    }

    fun getTableByName(name: String): Table? {
        return this.tables[name]
    }

    fun getByIdentifier(identifier: Ast.Expression.TableIdentifier): Table? {
        var table: Table?

        if (identifier.alias != null) {
            val tableName = this.tableAliases[identifier.alias!!.lexeme]

            if (tableName == null) {
                return null
            }

            table = this.tables[tableName]
        } else {
            table = this.tables[identifier.name.lexeme]
        }

        return table
    }

    fun setTable(tableName: String, table: Table) {
        this.tableAliases[tableName] = table.name
        this.tables.put(tableName, table)
    }

    fun getTableName(tableName: String): String? {
        return this.tableAliases[tableName]
    }

    fun updateBindings(bindings: Map<String, Table>) {
        this.tables += bindings
        this.tableAliases += bindings.flatMap {
            entry -> listOf(
                Pair(entry.key, entry.value.name),
                Pair(entry.value.name, entry.value.name)
            )
        }.toMap().toMutableMap()
    }
}

class Scope(
    val executionContext: ExecutionContext = ExecutionContext(),
    var parentScope: Scope? = null,
    val children: MutableList<Scope> = mutableListOf()
) {
    fun setTable(name: String, table: Table) {
        this.executionContext.setTable(name, table)
    }

    fun getLocalTables(): Iterable<Table> {
        return this.executionContext.tables.values
    }

    fun getLocalTableByName(name: String): Table? {
        return this.executionContext.getTableByName(name)
    }

    fun getTableByName(name: String): Table? {
        val found = this.getLocalTableByName(name)

        if (found != null) {
            return found
        }

        return this.parentScope?.getTableByName(name)
    }

    fun getByIdentifier(identifier: Ast.Expression.TableIdentifier): Table? {
        val found = this.executionContext.getByIdentifier(identifier)

        if (found != null) {
            return found
        }

        return this.parentScope?.getByIdentifier(identifier)
    }

    fun setTableAlias(alias: String, tableName: String) {
        this.executionContext.tableAliases[alias] = tableName
    }

    fun getLocalTableAlias(alias: String): String? {
        return this.executionContext.tableAliases[alias]
    }

    fun getTableAlias(alias: String): String? {
        val tableName = this.getLocalTableAlias(alias)

        if (tableName != null) {
            return tableName
        }

        return this.parentScope?.getTableAlias(alias)
    }

    fun stack(): Scope {
        val scope = Scope(parentScope = this)
        this.children.add(scope)

        return scope
    }

    fun pop(): Scope {
//        this.parentScope?.children?.remove(this)
//        this.parentScope = null

        return this
    }

    fun updateLocalBindings(bindings: Map<String, Table>) {
        this.executionContext.updateBindings(bindings)
    }
}
