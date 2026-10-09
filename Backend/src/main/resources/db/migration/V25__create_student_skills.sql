-- Student-declared skills with self-rated proficiency (Phase 1a).
-- Unlike skill_evidence (append-only observations feeding readiness), student_skills is the
-- current, mutable skill set a student maintains on their own profile: one row per skill,
-- upserted in place. It is what skill-gap analysis and semantic matching read as the student's
-- present ability, so it is the student's own editable record, not derived from a model.

create table student_skills (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    skill varchar(120) not null,
    proficiency integer not null,
    source varchar(32) not null,
    updated_at timestamptz not null,
    constraint student_skills_unique_skill unique (student_id, skill)
);

create index idx_student_skills_student on student_skills(student_id);
