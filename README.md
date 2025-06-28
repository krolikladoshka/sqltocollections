SQL parser test task. Done using recursive descent.

src/syntax/Ast — full AST

src/syntax/View — representation of the full AST mentioned in the test task description

-----
-----
There’s also an implementation of an sql interpreter which performs queries on collections.

The interpreter demonstrates how code maintainability can rapidly spiral out of control if the code is written only using hands without investing at least some brain power into the programming process.

Has fields scope evaluation bugs that could have been easily solved.

I didn’t plan to write it. It just happened. I wasn’t thinking about code organization or architecture. The initial idea of lazy evaluation failed (scope evaluation again) because of poor planning.

Does not support star (*) selects (though they’re parsed).

Does not support subqueries from an outer scope.

Does not support lateral subqueries.

Does not support group by expressions: the clause is only allowed to contain identifiers.

Only has avg, sum and count aggregation functions.

Only round() built-in function is supported.

Everything is assumed to be float or string, sometimes bool, null and int. I didn't want to implement typing

If you want to try executing something which I genuinely do not recommend, you should follow these rules when writing queries:

Everything that's not in a top level select must be aliased.

To order by a field from the top level select you should wrap your query in a subquery and reference the required fields by their fully qualified names.

If it still fails, that’s probably not your mistake. You may try to fight through all generated errors, though.
