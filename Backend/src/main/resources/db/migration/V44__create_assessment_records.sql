-- Bulk assessment import: per-test historical records.
--
-- Placement officers upload many students' assessment scores in one file (parsed and
-- posted by n8n later). Each uploaded row becomes one dated assessment_records row.
-- This is the append-only history table; the five current-snapshot values on `students`
-- (assessments_json + assessment_* columns from V19/V40) are recomputed from it as the
-- LATEST test per assessment_type (see AssessmentImportService).
--
-- Postgres-only. The H2 "test" profile runs with Flyway disabled and derives the schema
-- from the JPA entity (AssessmentRecord), including the unique constraint below, so the
-- two stay aligned. Prod runs ddl-auto: validate, so column names/types mirror the entity
-- (Hibernate's default camelCase -> snake_case naming).

CREATE TABLE assessment_records (
    id                varchar(64)   PRIMARY KEY,
    student_id        varchar(64)   NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    campus_id         varchar(64),
    assessment_type   varchar(32)   NOT NULL,   -- APTITUDE, CODING, TECHNICAL, COMMUNICATION, MOCK_INTERVIEW
    assessment_name   varchar(200)  NOT NULL,
    raw_score         numeric(6, 2) NOT NULL,
    max_score         numeric(6, 2) NOT NULL,
    normalized_score  integer       NOT NULL,   -- round(raw_score / max_score * 100), clamped 0..100
    assessment_date   date          NOT NULL,
    source            varchar(32)   NOT NULL DEFAULT 'BULK_IMPORT',
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT assessment_records_unique
        UNIQUE (student_id, assessment_type, assessment_name, assessment_date)
);

CREATE INDEX idx_assessment_records_student ON assessment_records(student_id);
CREATE INDEX idx_assessment_records_student_type_date
    ON assessment_records(student_id, assessment_type, assessment_date DESC);
