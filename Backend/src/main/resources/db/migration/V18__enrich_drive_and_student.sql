-- V18: enrich drives with display + full eligibility fields, and add student
-- 10th/12th percentages, so the live drive view and eligibility verdict match
-- the frontend contract (requiredSkills, rounds, allowedBranches, 10th/12th).
-- List fields are stored as JSON text, per the existing *_json convention
-- (readiness component_scores_json, risk factors_json, intervention actions_json).

alter table drives
    add column drive_type varchar(32),
    add column description varchar(2000),
    add column expected_hiring integer,
    add column venue varchar(200),
    add column minimum_tenth_percentage numeric(5, 2),
    add column minimum_twelfth_percentage numeric(5, 2),
    add column required_skills_json text,
    add column rounds_json text,
    add column allowed_branches_json text;

alter table students
    add column tenth_percentage numeric(5, 2),
    add column twelfth_percentage numeric(5, 2);

-- Backfill the V3 demo rows so the enriched drive view and the new
-- branch/percentage eligibility checks are demonstrable immediately.
update drives set
    drive_type = 'On-Campus',
    description = 'Backend Developer role building high-scale distributed services.',
    expected_hiring = 20,
    venue = 'Lab 2',
    minimum_tenth_percentage = 70,
    required_skills_json = '["Java","Spring Boot","SQL","REST APIs","Docker","AWS"]',
    rounds_json = '[{"name":"Online Assessment","description":"DSA + Java fundamentals, 90 minutes."},{"name":"Technical Interview 1","description":"Core CS + Spring Boot deep dive."},{"name":"HR Interview","description":"Culture fit and offer discussion."}]',
    allowed_branches_json = '["CSE","IT"]'
where id = 'drv_technova';

update drives set
    drive_type = 'On-Campus',
    description = 'Cloud Engineer role focused on automation and cloud-native deployment.',
    expected_hiring = 15,
    venue = 'Lab 2',
    required_skills_json = '["AWS","Docker","Kubernetes","Linux","Python"]',
    rounds_json = '[{"name":"Online Assessment","description":"Cloud fundamentals + scripting, 60 minutes."},{"name":"Technical Interview","description":"AWS + containerization scenarios."}]',
    allowed_branches_json = '["CSE","IT","ECE"]'
where id = 'drv_cloudsphere';

update students set tenth_percentage = 92.4, twelfth_percentage = 89.6 where id = 'stu_demo';
