-- V21: enrich interviews with the display fields the frontend InterviewSlot shows
-- (round, venue, panel, and a denormalized company name), and seed one demo interview
-- so the student Interviews screen renders a populated card live.

alter table interviews
    add column round varchar(120),
    add column venue varchar(200),
    add column panel varchar(200),
    add column company_name varchar(200);

insert into interviews (
    id, campus_id, student_id, drive_id, schedule_event_id, mode, scheduled_at,
    status, result, feedback, created_at, round, venue, panel, company_name)
values (
    'iv_demo_technova_t1', 'campus_demo', 'stu_demo', 'drv_technova', null, 'ON_CAMPUS',
    timestamptz '2026-09-22 10:30:00+05:30', 'SCHEDULED', 'PENDING', null, now(),
    'Technical Interview 1', 'Lab 2', 'Panel A — Dr. Anita Mishra', 'TechNova');
