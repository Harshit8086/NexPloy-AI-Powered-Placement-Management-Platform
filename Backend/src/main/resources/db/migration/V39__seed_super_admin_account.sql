-- Super Admin login account.
--
-- The frontend already models an `admin` role (Role union in types, roleDemoEmail
-- prefill of admin@nexploy.demo) and the backend UserRole enum already has ADMIN,
-- which CampusAccessService treats as cross-campus. Only the seeded account was
-- missing, so this adds it. Username is the email (admin@gmail.com) so email-based
-- sign-in works through DatabaseUserDetailsService (resolves accounts by primary key).
--
-- Password is BCrypt-hashed (verified against raw "Admin123"); the {bcrypt} prefix
-- routes it through Spring's DelegatingPasswordEncoder. campus_id is cmp_main to
-- satisfy the user_accounts -> campuses FK (V33); ADMIN can access every campus
-- regardless, so this value does not gate access.
-- Idempotent: re-running refreshes the password/role/campus without duplicating.

insert into user_accounts (username, password, campus_id, enabled, role)
values (
    'admin@gmail.com',
    '{bcrypt}$2b$10$r8Y5XkWn5L1cZSiHmk2VVO1BY4w11HAjyzYdqwbhWjHd60lXClVhq',
    'cmp_main',
    true,
    'ADMIN'
)
on conflict (username) do update set
    password = excluded.password,
    campus_id = excluded.campus_id,
    enabled = excluded.enabled,
    role = excluded.role;
