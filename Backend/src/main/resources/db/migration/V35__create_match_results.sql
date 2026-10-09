-- Phase 1 — persisted match results + shortlist decisions.
--
-- Today the matching engine computes a fit score + explanation into a MatchResponse
-- DTO and discards it. That blocks two problem-statement deliverables: explainable
-- decision records (why a student was selected / what was weak) and accuracy
-- evaluation over time. This table is the durable, auditable record of each scoring.
--
-- One row per (drive, student, model_version): re-running the same model version for
-- the same pair upserts; a new model version produces a new row so historical scores
-- are never overwritten. The *_json columns keep the structured breakdown/explanation
-- as emitted by the engine (dual with any future normalized form).

create table match_results (
    id                  varchar(80) primary key,
    campus_id           varchar(64),
    cycle_id            varchar(64),
    student_id          varchar(64) not null,
    drive_id            varchar(64) not null,
    overall_fit         integer not null,
    eligibility_passed  boolean not null default false,
    breakdown_json      text,
    why_selected_json   text,
    weak_or_missing_json text,
    recommendation      varchar(64),
    model_version       varchar(64) not null,
    computed_at         timestamptz not null,

    constraint match_results_unique_drive_student_model
        unique (drive_id, student_id, model_version),
    constraint match_results_campus_fk  foreign key (campus_id)  references campuses(id),
    constraint match_results_cycle_fk   foreign key (cycle_id)   references placement_cycles(id),
    constraint match_results_student_fk foreign key (student_id) references students(id) on delete cascade,
    constraint match_results_drive_fk   foreign key (drive_id)   references drives(id) on delete cascade
);

create index match_results_drive_idx   on match_results (drive_id);
create index match_results_student_idx on match_results (student_id);

-- An officer's accept/reject decision over a scored match. match_result_id is nullable
-- so a decision can still be recorded if the match was never persisted.
create table shortlist_decisions (
    id              varchar(80) primary key,
    match_result_id varchar(80),
    drive_id        varchar(64) not null,
    student_id      varchar(64) not null,
    decided_by      varchar(100) not null,
    decision        varchar(16) not null,
    reason          varchar(500),
    decided_at      timestamptz not null,

    constraint shortlist_decisions_match_fk
        foreign key (match_result_id) references match_results(id) on delete set null
);

create index shortlist_decisions_drive_idx on shortlist_decisions (drive_id);
