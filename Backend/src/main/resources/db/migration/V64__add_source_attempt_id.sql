-- Links a retry attempt to the completed attempt whose missed questions it re-draws.
-- NULL for every other attempt (official tests and AI practice sessions).
ALTER TABLE aptitude_attempts ADD COLUMN source_attempt_id varchar(64);
CREATE INDEX idx_apa_source_attempt ON aptitude_attempts (source_attempt_id);