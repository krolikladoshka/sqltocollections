select
    out.field2 as field2,
    out.count as count,
    round((out.f2_avg + out.f1_avg) / 2, 3) as avg_of_avg,
    round(out.f2_sum between 1 and 10, 1) as is_f2_sum_between_1_and_10,
    round(out.f2_sum, 3) as f2_sum
from
(
    select
        fc.field2 as field2,
        count(*) as count,
        sum(p.field2) as f2_sum,
        avg(p.field2) as f2_avg,
        avg(p.field1) as f1_avg
    from (
        select
            tc1.field1 as field1,
            (((tc4.field1 / 100.0) * 10.0) + (3.0 / 5.0) - 1 + (3 * (4 - 5))) as field2,
            (tc1.field1 in (1)) as field3
          from
            test_collection as tc1,
            filter_collection as tc3,
            test_collection as tc4
          where
--            field2 < 1
            tc1.field1 between 5 and 8 or false
    ) as p,
    filter_collection as fc,
    filter_collection as fc2,
    test_collection as tc3,
    filter_collection as fc1
    where
        p.field2 < 1
--        on fc1.field1 = p.field1 and true or true
    group by fc.field2
    having f2_sum < 10.0 or true
) as out
order by out.field2, out.f2_sum;