create table students (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    name varchar(200) not null,
    email varchar(320) not null,
    roll_number varchar(100) not null,
    branch varchar(120),
    branch_code varchar(32),
    graduation_year integer,
    cgpa numeric(4, 2),
    backlogs integer,
    readiness_score integer,
    placement_status varchar(32)
);

create table drives (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    recruiter_id varchar(64) not null,
    company_name varchar(200) not null,
    role varchar(200) not null,
    location varchar(200),
    package_lpa numeric(10, 2),
    minimum_cgpa numeric(4, 2),
    maximum_backlogs integer,
    graduation_year integer,
    deadline date,
    status varchar(32) not null
);

create table applications (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    drive_id varchar(64) not null,
    applied_on date,
    status varchar(32) not null
);

create table schedule_events (
    id varchar(64) primary key,
    drive_id varchar(64) not null,
    campus_id varchar(64) not null,
    venue_id varchar(64),
    panel_id varchar(64),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    expected_participants integer,
    venue_capacity integer,
    conflict boolean not null default false,
    constraint schedule_events_valid_interval check (ends_at > starts_at)
);

create index idx_students_campus on students(campus_id);
create index idx_drives_campus on drives(campus_id);
create index idx_applications_student on applications(student_id);
create index idx_applications_drive on applications(drive_id);
create index idx_schedule_events_campus_time on schedule_events(campus_id, starts_at, ends_at);
