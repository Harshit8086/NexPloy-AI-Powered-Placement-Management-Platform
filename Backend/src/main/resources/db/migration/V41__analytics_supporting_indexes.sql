-- Analytics SQL rewrite — supporting index for the set-based placement-summary aggregates.
-- The summary now issues COUNT/AVG/GROUP BY filtered by campus_id against students, drives,
-- offers and applications. drives(campus_id) and offers(campus_id) are already covered by the
-- composite (campus_id, status) indexes from V37; students had no campus_id index yet.
-- Applications carry no campus_id (scoped via a students subquery), so they rely on the FK
-- index on students(campus_id) plus applications(student_id). Plain, additive btree index.

create index if not exists students_campus_idx
    on students (campus_id);
