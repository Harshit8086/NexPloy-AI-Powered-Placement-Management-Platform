-- Raw evidence stored separately from derived scores (DESIGN.md section 2).
-- skill_evidence is the append-only system of record for observed skill levels;
-- readiness_snapshots is the append-only history of derived readiness results.
-- A snapshot references the evidence it was computed from but never mutates it.

create table skill_evidence (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    skill varchar(120) not null,
    source varchar(32) not null,
    level integer not null,
    detail varchar(500),
    recorded_by varchar(100),
    recorded_at timestamptz not null
);

create table readiness_snapshots (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    placement_cycle_id varchar(64) not null,
    score double precision not null,
    category varchar(32) not null,
    component_scores_json text,
    evidence_ids varchar(2000),
    confidence double precision not null,
    calculation_version varchar(64),
    model_version varchar(64),
    feature_version varchar(64),
    generated_at timestamptz not null,
    created_at timestamptz not null
);

create index idx_skill_evidence_student on skill_evidence(student_id);
create index idx_readiness_snapshots_student on readiness_snapshots(student_id);
