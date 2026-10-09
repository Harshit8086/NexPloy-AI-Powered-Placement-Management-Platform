-- Backlog history: append-only, per-student timeline of active-backlog counts over time.
--
-- students.backlogs stays the authoritative CURRENT count (and the only value readiness/eligibility
-- read); this table records HOW that count changed — one row is appended whenever it changes on a
-- self-edit or officer edit, plus optional officer-entered timeline notes. It turns the single
-- students.backlog_history integer into a real semester-by-semester trend for the profile.
--
-- Postgres-only. The H2 "test" profile runs with Flyway disabled and derives this schema from the
-- StudentBacklogHistory JPA entity, so column names/types mirror the entity (camelCase -> snake_case)
-- and prod's ddl-auto: validate stays satisfied.

CREATE TABLE student_backlog_history (
    id               varchar(64)  PRIMARY KEY,
    student_id       varchar(64)  NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    campus_id        varchar(64),
    semester         integer,
    active_backlogs  integer      NOT NULL,   -- backlog count as of this record
    cleared_backlogs integer,                 -- how many were cleared vs the previous record (nullable)
    note             varchar(500),
    recorded_by      varchar(255),
    recorded_at      timestamptz  NOT NULL
);

CREATE INDEX idx_student_backlog_history_student
    ON student_backlog_history(student_id, recorded_at DESC);

-- Baseline backfill: one row per existing student capturing their current active-backlog count, so
-- the timeline is never empty for students who predate this table. Deterministic id per student.
INSERT INTO student_backlog_history
    (id, student_id, campus_id, semester, active_backlogs, cleared_backlogs, note, recorded_by, recorded_at)
SELECT 'blh_seed_' || s.id, s.id, s.campus_id, s.semester, COALESCE(s.backlogs, 0), NULL,
       'Baseline captured at rollout', 'system', now()
FROM students s;
