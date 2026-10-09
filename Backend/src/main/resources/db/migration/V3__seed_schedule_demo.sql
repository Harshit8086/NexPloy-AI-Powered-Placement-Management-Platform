insert into students (
    id, campus_id, name, email, roll_number, branch, branch_code,
    graduation_year, cgpa, backlogs, readiness_score, placement_status
) values (
    'stu_demo', 'campus_demo', 'Demo Student', 'demo@student.example', 'DEMO-001',
    'Computer Science', 'CSE', 2026, 8.50, 0, 78, 'IN PROCESS'
);

insert into drives (
    id, campus_id, recruiter_id, company_name, role, location,
    package_lpa, minimum_cgpa, maximum_backlogs, graduation_year, deadline, status
) values (
    'drv_technova', 'campus_demo', 'rec_technova', 'TechNova', 'Backend Developer',
    'Campus', 12.00, 7.00, 1, 2026, '2026-09-30', 'ACTIVE'
);

insert into drives (
    id, campus_id, recruiter_id, company_name, role, location,
    package_lpa, minimum_cgpa, maximum_backlogs, graduation_year, deadline, status
) values (
    'drv_cloudsphere', 'campus_demo', 'rec_cloudsphere', 'CloudSphere', 'Cloud Engineer',
    'Campus', 14.00, 7.50, 1, 2026, '2026-09-30', 'ACTIVE'
);

insert into applications (id, student_id, drive_id, applied_on, status) values
('app_demo_technova', 'stu_demo', 'drv_technova', '2026-09-20', 'SHORTLISTED');

insert into applications (id, student_id, drive_id, applied_on, status) values
('app_demo_cloudsphere', 'stu_demo', 'drv_cloudsphere', '2026-09-20', 'SHORTLISTED');

insert into schedule_events (
    id, drive_id, campus_id, venue_id, panel_id, starts_at, ends_at,
    expected_participants, venue_capacity, conflict
) values (
    'evt_technova', 'drv_technova', 'campus_demo', 'lab-2', 'panel-a',
    '2026-09-22T10:00:00Z', '2026-09-22T12:00:00Z', 40, 80, true
);

insert into schedule_events (
    id, drive_id, campus_id, venue_id, panel_id, starts_at, ends_at,
    expected_participants, venue_capacity, conflict
) values (
    'evt_cloudsphere', 'drv_cloudsphere', 'campus_demo', 'lab-2', 'panel-a', '2026-09-22T11:00:00Z',
    '2026-09-22T13:00:00Z', 30, 80, true
);
