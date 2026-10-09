-- AI mock (practice) interviews — a NEW self-service capability, kept strictly separate from the
-- placement `interviews` table (V8). A student starts a practice run, the AI generates a set of
-- questions, the student answers them one at a time, each answer is scored, and a final summary
-- report is produced. Nothing here feeds placement eligibility, offers, readiness or the digital
-- twin: it is descriptive practice history only.
--
-- Postgres-only. The H2 "test" profile runs with Flyway disabled and derives the schema from the
-- JPA entities (MockInterview, MockInterviewQuestion), so the two must stay aligned. Prod runs
-- ddl-auto: validate, so column names/types mirror the entities (Hibernate's default camelCase ->
-- snake_case naming). FK columns are plain strings on the entity (as with `interviews`); the FK
-- constraints below are declared here for Postgres.

CREATE TABLE mock_interviews (
    id                varchar(64)   PRIMARY KEY,
    campus_id         varchar(64),
    student_id        varchar(64)   NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    type              varchar(32)   NOT NULL,   -- TECHNICAL, BEHAVIOURAL, CODING, MIXED
    role              varchar(200),
    use_resume        boolean       NOT NULL DEFAULT false,
    status            varchar(32)   NOT NULL,   -- IN_PROGRESS, COMPLETED
    current_question  integer       NOT NULL DEFAULT 0,   -- index of the next unanswered question
    overall_score     integer,                            -- 0..100, set on completion
    strengths         text,                               -- JSON array of strings, set on completion
    weaknesses        text,                               -- JSON array of strings, set on completion
    recommendations   text,                               -- JSON array of strings, set on completion
    summary           text,                               -- narrative report, set on completion
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL
);

CREATE TABLE mock_interview_questions (
    id                  varchar(64)  PRIMARY KEY,
    mock_interview_id   varchar(64)  NOT NULL REFERENCES mock_interviews(id) ON DELETE CASCADE,
    ordinal             integer      NOT NULL,   -- 0-based position within the interview
    prompt              text         NOT NULL,
    difficulty          varchar(32),             -- easy | medium | hard
    timer_seconds       integer,                 -- suggested answer time budget
    is_coding           boolean      NOT NULL DEFAULT false,
    language            varchar(32),             -- programming language for a coding question
    student_answer      text,
    score               integer,                 -- 0..100 for this answer, once evaluated
    feedback            text,                    -- JSON: 9 metric scores + feedback text + improvements
    answered_at         timestamptz
);

CREATE INDEX idx_mock_interviews_student ON mock_interviews(student_id);
CREATE INDEX idx_mock_interviews_campus ON mock_interviews(campus_id);
CREATE INDEX idx_mock_interview_questions_interview
    ON mock_interview_questions(mock_interview_id, ordinal);
