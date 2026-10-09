-- V42: CRUD support — soft-delete audit trail for drives.
--
-- The full-CRUD pass adds write paths (create/update/status) across placement
-- entities. Two new enum values are Java-only and need no DB change, because the
-- relevant columns are plain varchar:
--   * drives.status gains CANCELLED (soft-retire a drive without deleting history)
--   * applications.status gains WITHDRAWN (student self-withdrawal)
--
-- Status transitions that retire a drive (CLOSED / COMPLETED / CANCELLED) should be
-- attributable, mirroring offer_status_changes.changed_by / decided_by. drives had
-- no such trail, so add nullable audit columns; existing rows keep NULL (never
-- closed) and every future transition stamps who/when.
--
-- No unique index on (campus_id, lower(email)) is added here: the seed dataset was
-- not verified dupe-free in this environment and a partial/functional index is not
-- portable to the H2 test profile. Deferred — see complete.md "Remaining Issues".

alter table drives
    add column closed_at timestamptz,
    add column closed_by varchar(100);
