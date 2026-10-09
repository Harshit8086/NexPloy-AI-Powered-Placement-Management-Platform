-- Apply-to-drive (Phase 2a). Until now applications were seed-only and GET-only; this
-- migration makes them writable safely:
--   1. A student may hold at most one application per drive — enforced in the DB, not just
--      in the service, so a race between two concurrent applies cannot create a duplicate.
--   2. Optional application payload: a short cover note and the resume document the student
--      chose to attach. Both are nullable so existing rows and the seed data remain valid.

alter table applications add column cover_note varchar(2000);
alter table applications add column resume_document_id varchar(64);

alter table applications
    add constraint applications_unique_student_drive unique (student_id, drive_id);
