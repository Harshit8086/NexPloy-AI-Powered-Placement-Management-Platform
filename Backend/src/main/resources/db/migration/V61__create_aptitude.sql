-- Recreate the aptitude tables (V57 dropped them) for the two-lane hybrid module. Lane 1 (OFFICIAL):
-- timed MCQ tests drawn from the curated bank, deterministic scoring, and the completed score is
-- folded into assessment_records/readiness via the optional assessment bridge. Lane 2 (PRACTICE):
-- AI-generated questions owned by the student, never fed into readiness.
--
-- Questions carry a source and an optional student owner: bank rows are (student_id NULL, source
-- SEEDED/OFFICER); practice rows are (student_id = owner, source AI_GENERATED). Official draws
-- filter on source + student_id IS NULL so practice questions can never leak into a graded test.
-- Postgres only — the H2 test profile has Flyway disabled and derives its schema from the entities.

CREATE TABLE aptitude_questions (
  id varchar(64) PRIMARY KEY,
  campus_id varchar(64) REFERENCES campuses(id),       -- NULL = global bank question
  student_id varchar(64),                              -- AI_GENERATED owner; NULL for bank rows
  source varchar(16) NOT NULL DEFAULT 'SEEDED',        -- SEEDED | OFFICER | AI_GENERATED
  category varchar(32) NOT NULL,                       -- QUANTITATIVE|LOGICAL|VERBAL|DATA_INTERPRETATION
  difficulty varchar(16),
  prompt text NOT NULL,
  option_a text NOT NULL,
  option_b text NOT NULL,
  option_c text NOT NULL,
  option_d text NOT NULL,
  correct_option char(1) NOT NULL,                     -- 'A'..'D'
  explanation text,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_apq_category ON aptitude_questions (category, active);
CREATE INDEX idx_apq_source ON aptitude_questions (source, active);
CREATE INDEX idx_apq_campus ON aptitude_questions (campus_id);
CREATE INDEX idx_apq_student ON aptitude_questions (student_id);

CREATE TABLE aptitude_attempts (
  id varchar(64) PRIMARY KEY,
  student_id varchar(64) NOT NULL REFERENCES students(id) ON DELETE CASCADE,
  test_id varchar(32) NOT NULL,                        -- catalog test id; 'PRACTICE' on the practice lane
  mode varchar(16) NOT NULL DEFAULT 'OFFICIAL',        -- OFFICIAL | PRACTICE
  category varchar(32),                                -- single category, or NULL for the mixed test
  status varchar(16) NOT NULL DEFAULT 'IN_PROGRESS',   -- IN_PROGRESS | COMPLETED
  total_questions integer NOT NULL,
  correct_count integer,
  score numeric(5,2),                                  -- 0..100, deterministic percent correct
  duration_limit_seconds integer NOT NULL,
  time_spent_seconds integer,
  aptitude_before numeric(5,2),                        -- students.assessment_aptitude captured at submit
  started_at timestamptz NOT NULL DEFAULT now(),
  submitted_at timestamptz
);
CREATE INDEX idx_apa_student ON aptitude_attempts (student_id, started_at DESC);

CREATE TABLE aptitude_attempt_questions (
  id varchar(64) PRIMARY KEY,
  attempt_id varchar(64) NOT NULL REFERENCES aptitude_attempts(id) ON DELETE CASCADE,
  question_id varchar(64) NOT NULL REFERENCES aptitude_questions(id),
  ordinal integer NOT NULL,
  selected_option char(1),
  marked boolean NOT NULL DEFAULT false,
  correct boolean,
  UNIQUE (attempt_id, question_id)
);
CREATE INDEX idx_apaq_attempt ON aptitude_attempt_questions (attempt_id, ordinal);