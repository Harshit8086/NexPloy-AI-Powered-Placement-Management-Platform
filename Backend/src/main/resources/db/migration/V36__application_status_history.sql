-- Phase 1 — application status history (the placement funnel's audit trail).
--
-- applications.status defines the funnel (APPLIED -> SHORTLISTED -> ASSESSMENT ->
-- INTERVIEW -> SELECTED -> OFFER -> JOINED / REJECTED) but nothing recorded a
-- transition. This mirrors offer_status_changes: one immutable row per transition,
-- written by the service on every advance, so the dashboard has a single source of
-- pipeline truth and every stage change is attributable.

create table application_status_history (
    id              varchar(80) primary key,
    application_id  varchar(64) not null,
    previous_status varchar(32),
    new_status      varchar(32) not null,
    changed_by      varchar(100) not null,
    changed_at      timestamptz not null,

    constraint application_status_history_application_fk
        foreign key (application_id) references applications(id) on delete cascade
);

create index application_status_history_application_idx
    on application_status_history (application_id, changed_at);
