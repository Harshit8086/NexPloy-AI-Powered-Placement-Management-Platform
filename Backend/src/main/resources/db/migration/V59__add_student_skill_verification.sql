-- Skill verification: each student_skills row carries a verification level and optional proof link.
-- SELF_DECLARED (default) -> EVIDENCED (student attached a proof link) -> VERIFIED (passed an AI skill viva).
-- Set server-side only; re-rating proficiency never resets an earned status (see StudentSkillService).
-- Postgres-only (H2 test profile runs Flyway-disabled and derives these columns from the JPA entity).
ALTER TABLE student_skills ADD COLUMN verification_status varchar(32) NOT NULL DEFAULT 'SELF_DECLARED';
ALTER TABLE student_skills ADD COLUMN evidence_url  varchar(500);
ALTER TABLE student_skills ADD COLUMN evidence_note varchar(300);
