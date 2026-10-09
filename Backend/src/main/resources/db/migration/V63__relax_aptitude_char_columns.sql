-- V61 used char(1) for the single-letter option columns, but Postgres reports char(1) as
-- bpchar (Types#CHAR) while Hibernate maps Java String to a varchar type, so schema
-- validation fails on real boot. Widen both to varchar(1) — the convention V53 already
-- used — so the ORM and the database agree. No data change; values are still 'A'..'D'.

ALTER TABLE aptitude_questions
    ALTER COLUMN correct_option TYPE varchar(1);

ALTER TABLE aptitude_attempt_questions
    ALTER COLUMN selected_option TYPE varchar(1);