--SELECT author.name, count(book.id), sum(book.cost)
--FROM author
--LEFT JOIN book ON (author.id = book.author_id)
--GROUP BY author.name
--HAVING COUNT(*) > 1 AND SUM(book.cost) > 500
--LIMIT 10;
--SELECT name, count(id), sum(cost)
--select author.name, author.id, book.cost
----FROM author
----LEFT JOIN book ON (book.id = author.author_id)
----where book.cost < 300
----GROUP BY name
----
------having count > 1 and sum <> 500
----HAVING COUNT(*) > 1 AND SUM(book.cost) > 500
----LIMIT 10
--offset 15
--;
--a

select
    fc.field2,
    count(),
    sum(p.field2),
    avg(p.field2),
    avg(p.field1)
--    *
--    p.field1,
--    p.field2,
--    fc.field1,
--    fc.field2,
--    fc1.field1,
--    fc1.field2
--    sum(fc.field1)
--    fc.field1
--    p.field1 * 2,
--    case
--        when p.field1 < 4 then 'test'
--        when p.field1 between 4 and 6 then p.field1
--        when p.field1 in (6, 7, 11, 12) then 52
--        else 80
--    end,
--    p.field1 as pfield1,
--    p.field2 as new_field,
--    fc.field2 as testfield,
from (
    select field1 as field1, (field1 / 100) as field2
    from test_collection
--    where test_collection.field1 between 5 and 8
) as p
inner join filter_collection as fc
    on fc.field1 = p.field1
--cross join filter_collection as fc1
--group by fc.field2;
--where fc1.field2 = 'Test1'
--group by fc.field1
--    p.field1 in filter_collection
--    p.field1  > 5 and
--    p.field1 <= 6 or
--    p.field1 = 10 or
--    p.field1 <> 11 or
--    p.field2 > 2
--limit 3
--offset 2;