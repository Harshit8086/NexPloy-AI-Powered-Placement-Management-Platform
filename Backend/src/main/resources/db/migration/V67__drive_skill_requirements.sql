-- V67: Per-drive skill requirements with required proficiency levels and AI extraction provenance.
-- This replaces the legacy role_skill_requirements catalog table.
-- drive_id references drives(id) with on delete cascade.

create table if not exists drive_skill_requirements (
    id              varchar(64)   primary key,
    drive_id        varchar(64)   not null references drives(id) on delete cascade,
    skill           varchar(120)  not null,
    required_level  integer       not null check (required_level between 0 and 100),
    weight          integer       not null default 1 check (weight between 1 and 10),
    mandatory       boolean       not null default true,
    source          varchar(32)   not null default 'OFFICER',
    confidence      integer       check (confidence between 0 and 100),
    approved        boolean       not null default true,
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now()
);

create unique index if not exists ux_drive_skill_req
    on drive_skill_requirements (drive_id, skill);

create index if not exists ix_drive_skill_req_drive
    on drive_skill_requirements (drive_id);

create index if not exists ix_drive_skill_req_approved
    on drive_skill_requirements (drive_id, approved);
