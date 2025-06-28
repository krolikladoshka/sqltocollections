select
    subquery.two * 2.0 as checking_table_qualifier_access
from (
    select
        1 as one,
        2 as two,
        true as _true_,
        null as actual_null,
        1 between 0 and 3,
        case
            when 1 > 3 then 'Dead end'
            when 3 > 5 then 'Dead end 2'
            when 5 in (1, 2, 3, 4, 5, 6) then 'Should take this'
            when 6 in (1, 2, 3, 4, 5, 6) then 'But not this'
            else 'Dead end else'
        end as case_when_lazy,
        ((1 + 3 / 2 * (4 + 5 - (-7 * -1) + 100)) - 1 / 100 * 1000) * -1 as basic_expression,
        ((1 + 1) / 2 * 3 = (1 + 1) / 2 * 3) =
        (((4
            between 5
            and case
                    when 1 > 1 then true
                    when 1 >= 2 then true
                    when 1 <= 0 then true
                    when 1 <> 1 then true
                    when 1 = 2 then true
                    when 1 < 1 then true
                    else 7
                end
        ) or (not false and true))) as complex_expression,
        -- 'this comment is not executed' as non_executable_line
        'a simple string' as a_simple_string
) as subquery;