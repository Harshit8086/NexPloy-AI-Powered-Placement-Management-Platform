create table campuses (
    id varchar(64) primary key,
    name varchar(200) not null,
    city varchar(120),
    state varchar(120),
    active boolean not null default true
);

create table recruiters (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    name varchar(200) not null,
    company_name varchar(200) not null,
    email varchar(320) not null,
    active boolean not null default true,
    constraint recruiters_campus_fk foreign key (campus_id) references campuses(id)
);

create index idx_recruiters_campus on recruiters(campus_id);

insert into campuses (id, name, city, state, active)
values ('campus_demo', 'Demo Campus', 'Bengaluru', 'Karnataka', true);

insert into recruiters (id, campus_id, name, company_name, email, active)
values ('rec_technova', 'campus_demo', 'Asha Menon', 'TechNova', 'asha@technova.example', true),
       ('rec_cloudsphere', 'campus_demo', 'Rohan Das', 'CloudSphere', 'rohan@cloudsphere.example', true);
