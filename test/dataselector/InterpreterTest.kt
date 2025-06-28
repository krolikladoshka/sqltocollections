package dataselector

import assertAgainstMemoryDb
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class InterpreterTest {
    @Test
    fun execute() {
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "sql/simple_select.sql",
            "sql/simple_select_from_subquery.sql",
            "sql/expressions.sql",
            "sql/complex_nested_sql_with_all_lang_statements.sql",
            "sql/complex_nested_sql_with_all_lang_statements_1.sql",
            "sql/complex_nested_sql_with_all_lang_statements_2.sql",
            "sql/complex_nested_sql_with_all_lang_statements_3.sql",
            "sql/complex_nested_sql_with_all_lang_statements_4.sql",
        ]
    )
    fun evaluateExpression(sqlPath: String) {
        assertAgainstMemoryDb(sqlPath)
    }
}