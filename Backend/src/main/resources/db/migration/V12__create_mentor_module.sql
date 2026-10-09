create table mentor_assignments (
    id varchar(64) primary key,
    mentor_username varchar(100) not null,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    assigned_at timestamptz not null,
    constraint mentor_assignments_mentor_fk foreign key (mentor_username) references user_accounts(username),
    constraint mentor_assignments_student_fk foreign key (student_id) references students(id),
    constraint mentor_assignments_unique unique (mentor_username, student_id)
);

create table mentor_notes (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    author_username varchar(100) not null,
    note varchar(4000) not null,
    created_at timestamptz not null,
    constraint mentor_notes_student_fk foreign key (student_id) references students(id)
);

create index idx_mentor_assignments_mentor on mentor_assignments(mentor_username);
create index idx_mentor_assignments_student on mentor_assignments(student_id);
create index idx_mentor_notes_student on mentor_notes(student_id);

insert into user_accounts (username, password, campus_id, enabled, role)
values ('mentor', '{noop}change-me-now', 'campus_demo', true, 'MENTOR');

insert into mentor_assignments (id, mentor_username, student_id, campus_id, assigned_at)
values ('ma_demo', 'mentor', 'stu_demo', 'campus_demo', '2026-09-20T09:00:00Z');

insert into mentor_notes (id, student_id, campus_id, author_username, note, created_at)
values (
    'mn_demo', 'stu_demo', 'campus_demo', 'mentor',
    'Strong fundamentals; recommend mock interviews before the TechNova drive.',
    '2026-09-20T09:15:00Z'
);
