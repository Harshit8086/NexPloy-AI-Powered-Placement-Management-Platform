-- Phase 1 — supporting indexes for the hot read paths (dashboard + funnel + matches).
-- All are plain btree indexes on existing/new columns; safe and additive.

create index if not exists readiness_snapshots_student_time_idx
    on readiness_snapshots (student_id, generated_at desc);
create index if not exists risk_entries_student_time_idx
    on risk_entries (student_id, generated_at desc);
create index if not exists offers_campus_status_idx
    on offers (campus_id, status);
create index if not exists applications_drive_status_idx
    on applications (drive_id, status);
create index if not exists drives_campus_status_idx
    on drives (campus_id, status);
create index if not exists notifications_recipient_status_time_idx
    on notifications (recipient_username, status, created_at);
create index if not exists match_results_drive_fit_idx
    on match_results (drive_id, overall_fit desc);
