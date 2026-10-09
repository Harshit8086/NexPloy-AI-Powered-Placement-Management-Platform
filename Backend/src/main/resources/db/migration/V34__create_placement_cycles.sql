-- Phase 1 — placement cycles.
--
-- A placement cycle is the campus-scoped time box (an academic year / season) that
-- drives, applications, offers and the AI snapshots belong to. It gives analytics a
-- first-class period to group by instead of the free-text `placement_cycle_id` string
-- that readiness_snapshots / risk_entries carry today.
--
-- The legacy string columns are kept (dual, backward-compatible); the new nullable
-- cycle_id FK columns are added here and backfilled where a legacy value exists.

create table placement_cycles (
    id            varchar(64) primary key,
    campus_id     varchar(64) not null,
    name          varchar(160) not null,
    academic_year varchar(20),
    starts_on     date,
    ends_on       date,
    status        varchar(24) not null default 'ACTIVE',

    constraint placement_cycles_campus_fk
        foreign key (campus_id) references campuses(id)
);

create index placement_cycles_campus_idx on placement_cycles (campus_id);

-- Nullable cycle_id references (a row may predate any cycle).
alter table drives              add column cycle_id varchar(64);
alter table applications        add column cycle_id varchar(64);
alter table offers              add column cycle_id varchar(64);
alter table readiness_snapshots add column cycle_id varchar(64);
alter table risk_entries        add column cycle_id varchar(64);

alter table drives              add constraint drives_cycle_fk              foreign key (cycle_id) references placement_cycles(id);
alter table applications        add constraint applications_cycle_fk        foreign key (cycle_id) references placement_cycles(id);
alter table offers              add constraint offers_cycle_fk              foreign key (cycle_id) references placement_cycles(id);
alter table readiness_snapshots add constraint readiness_snapshots_cycle_fk foreign key (cycle_id) references placement_cycles(id);
alter table risk_entries        add constraint risk_entries_cycle_fk        foreign key (cycle_id) references placement_cycles(id);
