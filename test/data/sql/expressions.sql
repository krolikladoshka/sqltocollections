select
    out.tcfield1 as tcfield1,
    out.fcfield1 as fcfield1,
    out.fcfield2 as fcfield2,
    out.field2 as field2
from (
    select
        tc.field1 as tcfield1,
        fc.field1 as fcfield1,
        fc.field2 as fcfield2,
        (((tc.field1 / 100.0) * 10.0) + (3.0 / 50) - (1 + (3 * (4 - 5)))) as field2
    from
        test_collection as tc,
        filter_collection as fc
) as out
order by out.tcfield1;