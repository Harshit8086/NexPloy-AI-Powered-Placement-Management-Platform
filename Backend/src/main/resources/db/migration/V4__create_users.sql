create table user_accounts (
    username varchar(100) primary key,
    password varchar(200) not null,
    campus_id varchar(64),
    enabled boolean not null default true,
    role varchar(32) not null
);

insert into user_accounts (username, password, campus_id, enabled, role)
values ('officer', '{noop}change-me-now', 'campus_demo', true, 'OFFICER');
