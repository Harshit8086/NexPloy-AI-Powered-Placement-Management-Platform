-- V50: Multi-type drive scheduling — additive activity dimensions on schedule_events.
-- Existing rows (interview mirrors + seed data) backfill to INTERVIEW / INDIVIDUAL so the current
-- 1-to-1 interview scheduling behaviour is preserved exactly. No columns are dropped or renamed.

alter table schedule_events add column activity_type varchar(32);
alter table schedule_events add column participant_mode varchar(32);
alter table schedule_events add column participant_count integer;

update schedule_events
   set activity_type    = 'INTERVIEW',
       participant_mode  = 'INDIVIDUAL',
       participant_count = coalesce(expected_participants, 1)
 where activity_type is null;
