-- The student's personalized Career Roadmap: an ordered set of phases -> tasks that close the gaps
-- between their demonstrated skills and their target role. One row per student, materialized on first
-- view (GET /students/me/roadmap) from a curated, deterministic role->phase->task catalog and seeded
-- against the student's already-demonstrated skills (so demonstrated skills are pre-completed — the
-- roadmap never tells a student to "learn Java" when the resume shows Java).
--
-- roadmap_json holds the full phases[]->tasks[] structure with per-task status and any student-added
-- PROJECT tasks. Progress is COMPUTED from real task status, never fabricated. No raw resume text is
-- stored. This record is the student's own working plan; it is independent of student_goals (What-If)
-- and never feeds readiness, skill-gap or digital-twin calculations.

create table career_roadmap (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    -- Target role captured at materialization time; drives which catalog family was used.
    target_role varchar(200),
    -- The full StoredRoadmap serialized as JSON: phases[] each with tasks[] {id,title,detail,skill,
    -- kind,source,status,addressesGap}. No raw resume text.
    roadmap_json text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_career_roadmap_student unique (student_id)
);

create index idx_career_roadmap_student on career_roadmap(student_id);
