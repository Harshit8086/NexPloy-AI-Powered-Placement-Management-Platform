-- G3 (audit plan): duplicate-registration integrity — defense-in-depth at the database layer.
--
-- Registration already blocks a duplicate login case-insensitively at the account level
-- (RegistrationService.createAccount → existsByUsernameIgnoreCase → UsernameAlreadyExistsException,
-- 409). These functional unique indexes add the same guarantee one layer down, on the domain
-- profile tables, so no path (officer-created rows, bulk import, a concurrent race) can leave two
-- students or two recruiters sharing an email within one campus. email == username, so the scope is
-- (campus_id, lower(email)) — case-insensitive, per campus (the same person may legitimately exist
-- under different campuses).
--
-- Verified dupe-free before adding (audit plan Phase 0): 0 duplicate (campus_id, lower(email))
-- groups in either table on the seeded dataset. IF NOT EXISTS keeps this idempotent if the index
-- was created out-of-band. Postgres-only: the H2 test profile builds its schema from the JPA
-- entities with Flyway disabled, so this migration never runs there.

CREATE UNIQUE INDEX IF NOT EXISTS uq_students_campus_email
    ON students (campus_id, lower(email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_recruiters_campus_email
    ON recruiters (campus_id, lower(email));
