create table offers (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    student_id varchar(64) not null,
    drive_id varchar(64) not null,
    recruiter_id varchar(64) not null,
    role varchar(200) not null,
    package_lpa numeric(6, 2) not null,
    status varchar(32) not null,
    extended_at timestamptz not null,
    decided_at timestamptz,
    decided_by varchar(100),
    constraint offers_student_fk foreign key (student_id) references students(id),
    constraint offers_drive_fk foreign key (drive_id) references drives(id),
    constraint offers_recruiter_fk foreign key (recruiter_id) references recruiters(id)
);

create table offer_status_changes (
    id varchar(64) primary key,
    offer_id varchar(64) not null,
    previous_status varchar(32),
    new_status varchar(32) not null,
    changed_by varchar(100) not null,
    changed_at timestamptz not null,
    constraint offer_status_changes_offer_fk foreign key (offer_id) references offers(id)
);

create index idx_offers_campus on offers(campus_id);
create index idx_offers_student on offers(student_id);
create index idx_offer_status_changes_offer on offer_status_changes(offer_id);

insert into offers (
    id, campus_id, student_id, drive_id, recruiter_id, role,
    package_lpa, status, extended_at, decided_at, decided_by
) values (
    'off_demo_technova', 'campus_demo', 'stu_demo', 'drv_technova', 'rec_technova',
    'Backend Developer', 12.00, 'EXTENDED', '2026-09-23T10:00:00Z', null, null
);

insert into offer_status_changes (
    id, offer_id, previous_status, new_status, changed_by, changed_at
) values (
    'osc_demo_1', 'off_demo_technova', null, 'EXTENDED', 'officer', '2026-09-23T10:00:00Z'
);
