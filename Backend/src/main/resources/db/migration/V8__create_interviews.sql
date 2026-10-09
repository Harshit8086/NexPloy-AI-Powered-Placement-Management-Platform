create table interviews (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    student_id varchar(64) not null,
    drive_id varchar(64) not null,
    schedule_event_id varchar(64),
    mode varchar(32) not null,
    scheduled_at timestamptz,
    status varchar(32) not null,
    result varchar(32) not null default 'PENDING',
    feedback varchar(2000),
    created_at timestamptz not null,
    constraint interviews_student_fk foreign key (student_id) references students(id),
    constraint interviews_drive_fk foreign key (drive_id) references drives(id),
    constraint interviews_event_fk foreign key (schedule_event_id) references schedule_events(id)
);

create index idx_interviews_campus on interviews(campus_id);
create index idx_interviews_student on interviews(student_id);
create index idx_interviews_drive on interviews(drive_id);

insert into interviews (
    id, campus_id, student_id, drive_id, schedule_event_id,
    mode, scheduled_at, status, result, feedback, created_at
) values (
    'int_demo_technova', 'campus_demo', 'stu_demo', 'drv_technova', 'evt_technova',
    'ON_CAMPUS', '2026-09-22T10:00:00Z', 'SCHEDULED', 'PENDING', null, '2026-09-20T09:00:00Z'
);

insert into interviews (
    id, campus_id, student_id, drive_id, schedule_event_id,
    mode, scheduled_at, status, result, feedback, created_at
) values (
    'int_demo_cloudsphere', 'campus_demo', 'stu_demo', 'drv_cloudsphere', 'evt_cloudsphere',
    'VIRTUAL', '2026-09-22T11:00:00Z', 'SCHEDULED', 'PENDING', null, '2026-09-20T09:00:00Z'
);
