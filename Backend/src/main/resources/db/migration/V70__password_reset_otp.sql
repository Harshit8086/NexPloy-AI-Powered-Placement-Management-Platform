-- Separate registration verification challenges from account password recovery challenges.
-- Password-reset challenges do not require a campus association; the account is identified by email.
ALTER TABLE otp_verifications
    ADD COLUMN purpose varchar(32) NOT NULL DEFAULT 'REGISTRATION',
    ALTER COLUMN campus_id DROP NOT NULL;

CREATE INDEX idx_otp_email_purpose_created
    ON otp_verifications (lower(email), purpose, created_at DESC);
