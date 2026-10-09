-- Drops the aptitude-quiz schema created in V53 (seed data from V54 goes with the tables).
-- The aptitude quiz feature and its officer question-bank admin were removed; the APTITUDE
-- readiness/assessment dimension is unaffected (it lives in assessment_records + students.*,
-- which are shared with other producers and are intentionally NOT touched here).
-- Child-before-parent to respect the internal foreign keys declared in V53.
DROP TABLE IF EXISTS aptitude_attempt_questions;
DROP TABLE IF EXISTS aptitude_attempts;
DROP TABLE IF EXISTS aptitude_questions;
