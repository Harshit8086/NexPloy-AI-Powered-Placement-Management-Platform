-- Aptitude quiz — a NEW self-service capability. A shared, seeded question bank (V54) plus per-student
-- attempts. Unlike the AI mock interview, aptitude is a FIRST-CLASS assessment: submitting an attempt
-- writes an APTITUDE row into assessment_records (see AptitudeService) and therefore feeds readiness.
-- Scoring is deterministic (chosen option vs. stored correct option) — no AI, no model call.
--
-- Postgres-only. The H2 "test" profile runs with Flyway disabled and derives the schema from the JPA
-- entities (AptitudeQuestion, AptitudeAttempt, AptitudeAttemptQuestion), so column names/types here
-- mirror the entities (Hibernate's default camelCase -> snake_case naming). Prod runs
-- ddl-auto: validate. FK columns are plain strings on the entities; FK constraints are declared here.

CREATE TABLE aptitude_questions (
    id              varchar(64)  PRIMARY KEY,
    campus_id       varchar(64),                       -- NULL = global (all campuses); else campus-scoped
    category        varchar(64)  NOT NULL,             -- QUANTITATIVE, LOGICAL, VERBAL, DATA_INTERPRETATION
    difficulty      varchar(16),                       -- easy | medium | hard (advisory)
    prompt          text         NOT NULL,
    option_a        text         NOT NULL,
    option_b        text         NOT NULL,
    option_c        text         NOT NULL,
    option_d        text         NOT NULL,
    correct_option  varchar(1)   NOT NULL,             -- 'A' | 'B' | 'C' | 'D'
    explanation     text,                              -- shown only in the post-submission review
    active          boolean      NOT NULL DEFAULT true,-- soft-delete/retire; only active rows are drawn
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL
);

CREATE TABLE aptitude_attempts (
    id               varchar(64)  PRIMARY KEY,
    campus_id        varchar(64),
    student_id       varchar(64)  NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    category         varchar(64),                      -- NULL = mixed across categories
    status           varchar(16)  NOT NULL,            -- IN_PROGRESS, COMPLETED
    total_questions  integer      NOT NULL,
    correct_count    integer,                          -- set on submission
    score            integer,                          -- 0..100 = round(correct/total*100), set on submission
    started_at       timestamptz  NOT NULL,
    submitted_at     timestamptz
);

CREATE TABLE aptitude_attempt_questions (
    id               varchar(64)  PRIMARY KEY,
    attempt_id       varchar(64)  NOT NULL REFERENCES aptitude_attempts(id) ON DELETE CASCADE,
    question_id      varchar(64)  NOT NULL REFERENCES aptitude_questions(id),
    ordinal          integer      NOT NULL,            -- 0-based position within the attempt
    selected_option  varchar(1),                       -- student's chosen 'A'..'D'; NULL until answered
    correct          boolean                           -- set on submission
);

CREATE INDEX idx_aptitude_questions_category ON aptitude_questions(category, active);
CREATE INDEX idx_aptitude_questions_campus ON aptitude_questions(campus_id);
CREATE INDEX idx_aptitude_attempts_student ON aptitude_attempts(student_id, started_at DESC);
CREATE INDEX idx_aptitude_attempt_questions_attempt
    ON aptitude_attempt_questions(attempt_id, ordinal);
