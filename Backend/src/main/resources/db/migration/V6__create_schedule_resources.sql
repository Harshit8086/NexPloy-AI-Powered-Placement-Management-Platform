create table venues (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    name varchar(200) not null,
    capacity integer,
    active boolean not null default true
);

create table panels (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    name varchar(200) not null,
    active boolean not null default true
);

create table panel_members (
    id varchar(64) primary key,
    panel_id varchar(64) not null,
    member_name varchar(200) not null,
    member_email varchar(320),
    constraint panel_members_panel_fk foreign key (panel_id) references panels(id)
);

create table event_assignments (
    id varchar(64) primary key,
    event_id varchar(64) not null,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    assignment_role varchar(32) not null default 'CANDIDATE',
    created_at timestamptz not null,
    constraint event_assignments_event_fk foreign key (event_id) references schedule_events(id),
    constraint event_assignments_student_fk foreign key (student_id) references students(id),
    constraint event_assignments_unique unique (event_id, student_id)
);

create table blocked_slots (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    venue_id varchar(64),
    reason varchar(200) not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    constraint blocked_slots_venue_fk foreign key (venue_id) references venues(id),
    constraint blocked_slots_valid_interval check (ends_at > starts_at)
);

create table schedule_conflicts (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    conflict_type varchar(32) not null,
    event_id varchar(64) not null,
    conflicting_event_id varchar(64),
    resource_id varchar(64),
    overlap_start timestamptz,
    overlap_end timestamptz,
    message varchar(500),
    detected_at timestamptz not null
);

create index idx_venues_campus on venues(campus_id);
create index idx_panels_campus on panels(campus_id);
create index idx_panel_members_panel on panel_members(panel_id);
create index idx_event_assignments_event on event_assignments(event_id);
create index idx_event_assignments_student on event_assignments(student_id);
create index idx_blocked_slots_campus_time on blocked_slots(campus_id, starts_at, ends_at);
create index idx_schedule_conflicts_campus on schedule_conflicts(campus_id);

insert into venues (id, campus_id, name, capacity, active) values
    ('lab-2', 'campus_demo', 'Lab 2', 80, true),
    ('lab-3', 'campus_demo', 'Lab 3', 60, true);

insert into panels (id, campus_id, name, active) values
    ('panel-a', 'campus_demo', 'Panel A', true),
    ('panel-b', 'campus_demo', 'Panel B', true);

insert into panel_members (id, panel_id, member_name, member_email) values
    ('pm_a_1', 'panel-a', 'Asha Menon', 'asha@technova.example'),
    ('pm_a_2', 'panel-a', 'Rohan Das', 'rohan@cloudsphere.example'),
    ('pm_b_1', 'panel-b', 'Neha Kulkarni', 'neha@campus.example');

insert into event_assignments (id, event_id, student_id, campus_id, assignment_role, created_at) values
    ('asg_technova_demo', 'evt_technova', 'stu_demo', 'campus_demo', 'CANDIDATE', '2026-09-20T09:00:00Z'),
    ('asg_cloudsphere_demo', 'evt_cloudsphere', 'stu_demo', 'campus_demo', 'CANDIDATE', '2026-09-20T09:00:00Z');

insert into blocked_slots (id, campus_id, venue_id, reason, starts_at, ends_at) values
    ('blk_endsem', 'campus_demo', 'lab-3', 'End-semester examination', '2026-09-22T14:00:00Z', '2026-09-22T17:00:00Z');
