-- V19: enrich the student profile so StudentResponse can back the frontend
-- Student shape (profile, digital twin, readiness screens) without fabricating.
-- Scalars are columns; list/object fields are JSON text, per the *_json convention.
-- breakdown_json / readiness_trend_json are convenience caches on the student, the
-- same role students.readiness_score already plays; readiness_snapshots remains the
-- authoritative append-only derived-score history.

alter table students
    add column department varchar(200),
    add column batch varchar(64),
    add column semester integer,
    add column year_label varchar(64),
    add column backlog_history integer,
    add column resume_score integer,
    add column placement_probability integer,
    add column target_role varchar(200),
    add column risk_level varchar(16),
    add column risk_reason varchar(500),
    add column strengths_json text,
    add column critical_gaps_json text,
    add column certifications_json text,
    add column projects_json text,
    add column assessments_json text,
    add column breakdown_json text,
    add column readiness_trend_json text,
    add column preferred_roles_json text,
    add column preferred_domains_json text,
    add column preferred_locations_json text;

-- Backfill the demo student so the enriched profile is demonstrable immediately.
update students set
    department = 'School of Engineering',
    batch = '2022 - 2026',
    semester = 8,
    year_label = 'Final Year',
    backlog_history = 0,
    resume_score = 76,
    placement_probability = 68,
    target_role = 'Backend Developer',
    risk_level = 'LOW',
    risk_reason = 'On Track',
    tenth_percentage = 92.4,
    twelfth_percentage = 89.6,
    strengths_json = '["Java","Spring Boot","SQL","REST APIs","Problem Solving"]',
    critical_gaps_json = '["Docker","AWS","Communication"]',
    certifications_json = '["Oracle Certified Java Associate","MongoDB Developer Essentials"]',
    projects_json = '[{"name":"Campus Payments API","stack":["Java","Spring Boot","PostgreSQL"],"summary":"Transactional payments service with idempotent ledger writes and role-based access."},{"name":"Library Microservice","stack":["Java","Spring Cloud","MySQL"],"summary":"Service-to-service catalogue system with circuit breakers and centralized config."}]',
    assessments_json = '{"aptitude":79,"coding":74,"technical":82,"communication":69,"mockInterview":72}',
    breakdown_json = '{"academic":86,"technical":82,"coding":74,"aptitude":79,"communication":69,"projects":88,"interview":72,"placementActivity":76}',
    readiness_trend_json = '[58,62,68,74,78]',
    preferred_roles_json = '["Backend Developer","Cloud Engineer","Platform Engineer"]',
    preferred_domains_json = '["Fintech","Cloud Infrastructure","Enterprise SaaS"]',
    preferred_locations_json = '["Bengaluru","Hyderabad","Pune"]'
where id = 'stu_demo';
