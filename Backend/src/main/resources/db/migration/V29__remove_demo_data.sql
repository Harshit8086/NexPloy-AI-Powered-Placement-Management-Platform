-- ============================================================================
-- V29 — Remove demo/seed BUSINESS data. Backend becomes the single source of
-- truth: the running app must never fabricate business data. After this runs
-- the business tables are empty and the UI shows empty/loading/error states
-- until real data is registered.
--
-- KEPT (bootstrap only, so login + campus-scoped screens still resolve):
--   * user_accounts 'Officer123@gmail.com'  (email login, bcrypt of "Officer123", from V24)
--   * campuses 'cmp_main'                    (the frontend DEFAULT_CAMPUS_ID / bootstrap campus)
--
-- DELETED: every seeded row from V3/V5/V6/V8-V12/V21/V23/V24 across the business
-- tables, plus the demo logins 'officer' (V4) and 'mentor' (V12).
--
-- Every business table below contains ONLY seed data at this point (no real
-- records exist yet), so unqualified DELETEs are correct and also robust to
-- V23 having re-homed the originally-'campus_demo' recruiters/drives onto
-- 'cmp_main'. Deletes run children-before-parents to respect the FK graph.
-- Pure DML only — no DDL; ddl-auto stays 'validate'. Portable across H2/Postgres.
-- ============================================================================

-- 1. Offers subtree (offer_status_changes -> offers -> {students, drives, recruiters})
delete from offer_status_changes;
delete from offers;

-- 2. Notifications subtree
delete from notification_delivery_attempts;
delete from notifications;

-- 3. Documents (-> students)
delete from documents;

-- 4. Interviews (-> students, drives, schedule_events)
delete from interviews;

-- 5. Mentor module (mentor_notes, mentor_assignments -> user_accounts/students)
delete from mentor_notes;
delete from mentor_assignments;

-- 6. Schedule resources (event_assignments, blocked_slots -> schedule_events/venues,
--    then schedule_events, panels subtree, venues, conflicts)
delete from event_assignments;
delete from blocked_slots;
delete from schedule_events;
delete from panel_members;
delete from panels;
delete from venues;
delete from schedule_conflicts;

-- 7. Applications (no incoming FK)
delete from applications;

-- 8. Per-student child rows with no FK back-reference
delete from student_skills;
delete from student_goals;

-- 9. Core business rows (students -> drives -> recruiters)
delete from students;
delete from drives;
delete from recruiters;

-- 10. Campuses: keep only the bootstrap campus the kept officer is scoped to.
delete from campuses where id <> 'cmp_main';

-- 11. Refresh tokens for the demo logins must go before the accounts they
--     reference (refresh_tokens.username -> user_accounts, FK from V7); a demo
--     login that was ever used at runtime leaves a token row that would block
--     the account delete. No-op on a fresh DB (tokens are created only at login).
delete from refresh_tokens where username in ('officer', 'mentor');

-- 12. Demo login accounts: keep only the bootstrap officer (Officer123@gmail.com).
delete from user_accounts where username in ('officer', 'mentor');
