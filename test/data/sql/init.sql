create table test_collection (
    field1 double not null
);

create table filter_collection (
    field1 double not null,
    field2 varchar not null
);

create table another_collection (
    id int4 primary key not null
);

insert into test_collection ("field1")
values
    (1.0),
    (6.0),
    (7.0),
    (7.0),
    (8.0);

insert into filter_collection ("field1", "field2")
values
    (6.0, 'test1'),
    (7.0, 'Test2'),
    (8.0, 'Test3'),
    (9.0, 'Test4');