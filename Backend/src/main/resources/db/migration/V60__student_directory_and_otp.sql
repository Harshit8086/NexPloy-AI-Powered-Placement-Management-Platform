-- Central student directory (V60): the students table doubles as the officer-managed directory.
-- Officer-created rows carry source = 'DIRECTORY' and exist without a user account until the
-- student registers and claims the row; legacy/self-registered rows backfill to SELF_REGISTERED.
ALTER TABLE students ADD COLUMN source varchar(32) NOT NULL DEFAULT 'SELF_REGISTERED';
ALTER TABLE students ADD COLUMN active boolean NOT NULL DEFAULT true;

-- Registration OTP: proves the registrant owns the directory email before the account is created.
-- No FK from email is possible (the account does not exist yet); campus is pre-validated by the
-- service, but constrained here for integrity.
CREATE TABLE otp_verifications (
    id varchar(64) PRIMARY KEY,
    email varchar(320) NOT NULL,
    campus_id varchar(64) NOT NULL,
    code_hash varchar(200) NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    consumed boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    constraint otp_verifications_campus_fk foreign key (campus_id) references campuses(id)
);
CREATE INDEX idx_otp_email_campus ON otp_verifications (lower(email), campus_id);
