-- Phase 2: normalize student certifications & projects into child tables, and the
-- five assessment scores into columns on students.
--
-- Backward-compatible / dual-write: the JSON columns on `students`
-- (certifications_json, projects_json, assessments_json) are RETAINED and kept in sync
-- by StudentProfileNormalizationService on every profile edit. These normalized rows are
-- an ADDITIVE mirror that a later phase can promote to the sole source of truth before
-- the JSON columns are retired. Application reads still come from the JSON columns, so
-- this migration cannot regress existing read paths.
--
-- The backfill below parses the existing JSON with PostgreSQL json functions. It is
-- Postgres-only (the H2 test profile runs with Flyway disabled and never executes this)
-- and idempotent: primary keys are deterministic md5 hashes and inserts use
-- ON CONFLICT DO NOTHING, so re-running is a no-op. It runs AFTER the V38 canonical seed,
-- so a fresh demo database is mirrored from the seeded JSON automatically.

-- ---------- schema ----------
CREATE TABLE IF NOT EXISTS student_certifications (
    id          varchar(64)  PRIMARY KEY,
    student_id  varchar(64)  NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    campus_id   varchar(64),
    name        varchar(300) NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS student_projects (
    id          varchar(64)  PRIMARY KEY,
    student_id  varchar(64)  NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    campus_id   varchar(64),
    name        varchar(300) NOT NULL,
    summary     text,
    stack_json  text,
    sort_order  integer      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_student_certifications_student ON student_certifications(student_id);
CREATE INDEX IF NOT EXISTS idx_student_projects_student ON student_projects(student_id);

ALTER TABLE students ADD COLUMN IF NOT EXISTS assessment_aptitude       integer;
ALTER TABLE students ADD COLUMN IF NOT EXISTS assessment_coding         integer;
ALTER TABLE students ADD COLUMN IF NOT EXISTS assessment_technical      integer;
ALTER TABLE students ADD COLUMN IF NOT EXISTS assessment_communication  integer;
ALTER TABLE students ADD COLUMN IF NOT EXISTS assessment_mock_interview integer;

-- ---------- backfill: certifications (JSON array of strings) ----------
INSERT INTO student_certifications (id, student_id, campus_id, name, sort_order)
SELECT 'stucert_' || md5(s.id || ':' || c.ord),
       s.id, s.campus_id, c.value, (c.ord - 1)::int
FROM students s
CROSS JOIN LATERAL json_array_elements_text(s.certifications_json::json) WITH ORDINALITY AS c(value, ord)
WHERE s.certifications_json IS NOT NULL
  AND btrim(s.certifications_json) <> ''
  AND left(btrim(s.certifications_json), 1) = '['
  AND c.value IS NOT NULL
  AND btrim(c.value) <> ''
ON CONFLICT (id) DO NOTHING;

-- ---------- backfill: projects (JSON array of {name, stack[], summary}) ----------
INSERT INTO student_projects (id, student_id, campus_id, name, summary, stack_json, sort_order)
SELECT 'stuproj_' || md5(s.id || ':' || p.ord),
       s.id, s.campus_id,
       COALESCE(NULLIF(btrim(p.elem->>'name'), ''), 'Untitled project'),
       p.elem->>'summary',
       (p.elem->'stack')::text,
       (p.ord - 1)::int
FROM students s
CROSS JOIN LATERAL json_array_elements(s.projects_json::json) WITH ORDINALITY AS p(elem, ord)
WHERE s.projects_json IS NOT NULL
  AND btrim(s.projects_json) <> ''
  AND left(btrim(s.projects_json), 1) = '['
ON CONFLICT (id) DO NOTHING;

-- ---------- backfill: assessments (JSON object -> columns, 1:1 with student) ----------
UPDATE students s SET
    assessment_aptitude       = NULLIF(s.assessments_json::json->>'aptitude', '')::int,
    assessment_coding         = NULLIF(s.assessments_json::json->>'coding', '')::int,
    assessment_technical      = NULLIF(s.assessments_json::json->>'technical', '')::int,
    assessment_communication  = NULLIF(s.assessments_json::json->>'communication', '')::int,
    assessment_mock_interview = NULLIF(s.assessments_json::json->>'mockInterview', '')::int
WHERE s.assessments_json IS NOT NULL
  AND btrim(s.assessments_json) <> ''
  AND left(btrim(s.assessments_json), 1) = '{';
