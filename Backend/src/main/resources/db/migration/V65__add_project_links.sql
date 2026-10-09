-- V65: project links — GitHub repo + live demo URL on the normalized project rows (V40 table).
-- The read path still comes from students.projects_json (schemaless, so it carries the new
-- fields without change); these columns keep the dual-write mirror complete.
ALTER TABLE student_projects ADD COLUMN IF NOT EXISTS github_url varchar(500);
ALTER TABLE student_projects ADD COLUMN IF NOT EXISTS live_url   varchar(500);