-- Work experience / internships, stored as a JSON array of
-- {company, role, duration, summary} — mirrors projects_json.
alter table students
    add column experience_json text;
