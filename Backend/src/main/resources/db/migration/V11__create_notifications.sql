create table notifications (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    recipient_username varchar(100) not null,
    channel varchar(32) not null,
    subject varchar(200) not null,
    body varchar(4000) not null,
    status varchar(32) not null default 'PENDING',
    attempt_count integer not null default 0,
    created_at timestamptz not null,
    last_attempt_at timestamptz,
    read_at timestamptz
);

create table notification_delivery_attempts (
    id varchar(64) primary key,
    notification_id varchar(64) not null,
    attempt_number integer not null,
    outcome varchar(32) not null,
    detail varchar(500),
    attempted_at timestamptz not null,
    constraint delivery_attempts_notification_fk foreign key (notification_id) references notifications(id)
);

create index idx_notifications_campus on notifications(campus_id);
create index idx_notifications_recipient on notifications(recipient_username);
create index idx_delivery_attempts_notification on notification_delivery_attempts(notification_id);

insert into notifications (
    id, campus_id, recipient_username, channel, subject, body,
    status, attempt_count, created_at, last_attempt_at, read_at
) values (
    'ntf_demo_interview', 'campus_demo', 'officer', 'EMAIL',
    'Interview scheduled: TechNova', 'Your TechNova interview is scheduled for 2026-09-22 10:00.',
    'SENT', 1, '2026-09-20T09:05:00Z', '2026-09-20T09:05:05Z', null
);

insert into notification_delivery_attempts (
    id, notification_id, attempt_number, outcome, detail, attempted_at
) values (
    'nda_demo_1', 'ntf_demo_interview', 1, 'SENT', 'Accepted by mail relay', '2026-09-20T09:05:05Z'
);
