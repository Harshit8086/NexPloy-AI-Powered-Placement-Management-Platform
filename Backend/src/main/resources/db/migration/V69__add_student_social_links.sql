-- V69: student social links — LinkedIn + GitHub profile URLs displayed on the profile hero.
-- Self-reported through PUT /api/students/me (and the officer PUT /api/students/{id}); both are
-- optional and stay null until set. Plain scalar columns, so the read path needs no change.
ALTER TABLE students ADD COLUMN IF NOT EXISTS linkedin_url varchar(500);
ALTER TABLE students ADD COLUMN IF NOT EXISTS github_url   varchar(500);