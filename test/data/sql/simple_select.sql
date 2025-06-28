select
    round(1, 1) as one,
    round(2, 1) as two,
    round(true, 1) as _true_,
    round(1 between 0 and 3, 1) as one_between_0_and_3,
    case
        when 1 > 3 then 'Dead end'
        when 3 > 5 then 'Dead end 2'
        when 5 in (1, 2, 3, 4, 5, 6) then 'Should take this'
        when 6 in (1, 2, 3, 4, 5, 6) then 'But not this'
        else 'Dead end else'
    end as case_when_lazy,
    round(((1 + 3.0 / 2.0 * (4 + 5 - (-7 * -1) + 100)) - 1 / 100.0 * 1000.0) * -1, 3) as basic_expression,
    round(((1 + 1) / 2.0 * 3.0 = (1 + 1) / 2.0 * 3.0) =
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
    ) or (not false and true))), 1) as complex_expression,
    -- 'this comment is not executed' as non_executable_line
    'a simple string' as a_simple_string
;