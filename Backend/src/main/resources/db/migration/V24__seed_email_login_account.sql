-- Email-keyed login account.
--
-- The login form submits the Email field as the `username` to /api/auth/login,
-- and DatabaseUserDetailsService resolves accounts by primary key (username).
-- Seeding the account with the email as its username makes email-based sign-in
-- work without touching the frontend.
--
-- Password is BCrypt-hashed (verified against raw "Officer123"); the {bcrypt}
-- prefix routes it through Spring's DelegatingPasswordEncoder.
-- Idempotent: re-running refreshes the password/role/campus without duplicating.

insert into user_accounts (username, password, campus_id, enabled, role)
values (
    'Officer123@gmail.com',
    '{bcrypt}$2b$10$f5OVpPAco4Et3.1EuzkNb.YnVpw/hAvfJrOFzTEXWUd3N2zPP74qa',
    'cmp_main',
    true,
    'OFFICER'
)
on conflict (username) do update set
    password = excluded.password,
    campus_id = excluded.campus_id,
    enabled = excluded.enabled,
    role = excluded.role;
