-- The roadmap/goal a student commits to via "Apply This Roadmap" in the What-If Simulator (Phase 3b).
-- This is the student's own chosen growth plan: the What-If factors they selected, the projected
-- placement probability that scenario yields, and the role context it targets. One active row per
-- student, upserted in place on each re-apply, so the applied roadmap is retrievable after refresh
-- or re-login. It is intentionally separate from the authoritative readiness/assessment inputs —
-- persisting a goal here never changes readiness, skill-gap or digital-twin calculations.

create table student_goals (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    -- JSON array of selected What-If factor keys, e.g. ["aws","dsa","mockInterviews"].
    factors_json text not null,
    -- Role the roadmap is aimed at, captured from the student's target role at apply time.
    target_role varchar(200),
    baseline_probability integer,
    projected_probability integer,
    readiness_impact integer,
    recommended_path text,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint student_goals_unique_student unique (student_id)
);

create index idx_student_goals_student on student_goals(student_id);
