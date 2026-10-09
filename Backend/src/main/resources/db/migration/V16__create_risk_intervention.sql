-- Persisted risk -> intervention workflow (PRD/DESIGN: RiskEntry + InterventionPlan contract
-- entities, and the InterventionAssigned domain event). risk_entries is append-only history of
-- support-oriented risk scores; intervention_plans is the tracked, mutable support workflow
-- (PROPOSED -> ACTIVE -> COMPLETED) with action items and derived progress.

create table risk_entries (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    placement_cycle_id varchar(64) not null,
    level varchar(16) not null,
    support_probability double precision not null,
    primary_reason varchar(500),
    factors_json text,
    recommended_interventions_json text,
    confidence double precision not null,
    model_version varchar(64),
    feature_version varchar(64),
    generated_at timestamptz not null,
    created_at timestamptz not null
);

create table intervention_plans (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    mentor_username varchar(100) not null,
    risk_entry_id varchar(64),
    status varchar(16) not null default 'PROPOSED',
    focus_areas_json text,
    actions_json text,
    progress integer not null default 0,
    notes varchar(2000),
    created_by varchar(100),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index idx_risk_entries_student on risk_entries(student_id);
create index idx_intervention_plans_student on intervention_plans(student_id);
create index idx_intervention_plans_mentor on intervention_plans(mentor_username);
