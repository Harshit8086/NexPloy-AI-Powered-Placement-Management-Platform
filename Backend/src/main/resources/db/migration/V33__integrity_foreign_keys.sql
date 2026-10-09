-- Phase 1 — referential integrity for the core placement graph.
--
-- The V1 core tables (students, drives, applications, schedule_events) and the
-- later AI/analytics satellite tables were created without foreign keys, and
-- campus_id was never constrained anywhere. This migration adds those FKs so a
-- deleted parent can no longer orphan its children and a bad campus_id is rejected.
--
-- Repair-before-constrain: the DB is near-empty after V29, so these guards are
-- expected to be no-ops, but they make the migration safe on a populated DB too.
-- NOT NULL ref columns cannot be nulled, so genuine orphans are deleted; nullable
-- ref columns are nulled instead.

-- 1. Repair dangling references (no-op on a clean DB) -------------------------

-- Nullable campus refs -> null out unknowns.
update user_accounts set campus_id = null where campus_id is not null
    and campus_id not in (select id from campuses);
update domain_events set campus_id = null where campus_id is not null
    and campus_id not in (select id from campuses);

-- NOT NULL campus refs -> delete orphans (children first where relevant).
delete from skill_evidence        where campus_id not in (select id from campuses);
delete from readiness_snapshots   where campus_id not in (select id from campuses);
delete from risk_entries          where campus_id not in (select id from campuses);
delete from intervention_plans    where campus_id not in (select id from campuses);
delete from student_skills        where campus_id not in (select id from campuses);
delete from student_goals         where campus_id not in (select id from campuses);
delete from resume_analysis       where campus_id not in (select id from campuses);
delete from career_roadmap        where campus_id not in (select id from campuses);
delete from documents             where campus_id not in (select id from campuses);
delete from notifications         where campus_id not in (select id from campuses);
delete from mentor_assignments    where campus_id not in (select id from campuses);
delete from mentor_notes          where campus_id not in (select id from campuses);
delete from event_assignments     where campus_id not in (select id from campuses);
delete from venues                where campus_id not in (select id from campuses);
delete from panels                where campus_id not in (select id from campuses);
delete from blocked_slots         where campus_id not in (select id from campuses);
delete from schedule_conflicts    where campus_id not in (select id from campuses);
delete from interviews            where campus_id not in (select id from campuses);
delete from offers                where campus_id not in (select id from campuses);
delete from schedule_events       where campus_id not in (select id from campuses);
delete from students              where campus_id not in (select id from campuses);
delete from drives                where campus_id not in (select id from campuses);

-- Entity refs -> delete orphans.
delete from applications where student_id not in (select id from students)
    or drive_id not in (select id from drives);
delete from drives where recruiter_id not in (select id from recruiters);
delete from notifications where recipient_username not in (select username from user_accounts);

-- 2. campus_id -> campuses(id) on every campus-scoped table (R9 multi-tenancy) --

alter table students          add constraint students_campus_fk          foreign key (campus_id) references campuses(id);
alter table drives            add constraint drives_campus_fk            foreign key (campus_id) references campuses(id);
alter table schedule_events   add constraint schedule_events_campus_fk   foreign key (campus_id) references campuses(id);
alter table interviews        add constraint interviews_campus_fk        foreign key (campus_id) references campuses(id);
alter table offers            add constraint offers_campus_fk            foreign key (campus_id) references campuses(id);
alter table documents         add constraint documents_campus_fk         foreign key (campus_id) references campuses(id);
alter table notifications     add constraint notifications_campus_fk     foreign key (campus_id) references campuses(id);
alter table skill_evidence    add constraint skill_evidence_campus_fk    foreign key (campus_id) references campuses(id);
alter table readiness_snapshots add constraint readiness_snapshots_campus_fk foreign key (campus_id) references campuses(id);
alter table risk_entries      add constraint risk_entries_campus_fk      foreign key (campus_id) references campuses(id);
alter table intervention_plans add constraint intervention_plans_campus_fk foreign key (campus_id) references campuses(id);
alter table student_skills    add constraint student_skills_campus_fk    foreign key (campus_id) references campuses(id);
alter table student_goals     add constraint student_goals_campus_fk     foreign key (campus_id) references campuses(id);
alter table resume_analysis   add constraint resume_analysis_campus_fk   foreign key (campus_id) references campuses(id);
alter table career_roadmap    add constraint career_roadmap_campus_fk    foreign key (campus_id) references campuses(id);
alter table mentor_assignments add constraint mentor_assignments_campus_fk foreign key (campus_id) references campuses(id);
alter table mentor_notes      add constraint mentor_notes_campus_fk      foreign key (campus_id) references campuses(id);
alter table event_assignments add constraint event_assignments_campus_fk foreign key (campus_id) references campuses(id);
alter table venues            add constraint venues_campus_fk            foreign key (campus_id) references campuses(id);
alter table panels            add constraint panels_campus_fk            foreign key (campus_id) references campuses(id);
alter table blocked_slots     add constraint blocked_slots_campus_fk     foreign key (campus_id) references campuses(id);
alter table schedule_conflicts add constraint schedule_conflicts_campus_fk foreign key (campus_id) references campuses(id);

-- Nullable campus refs (row may legitimately have no campus).
alter table user_accounts     add constraint user_accounts_campus_fk     foreign key (campus_id) references campuses(id);
alter table domain_events     add constraint domain_events_campus_fk     foreign key (campus_id) references campuses(id);

-- 3. Core placement-graph entity references -----------------------------------

alter table applications add constraint applications_student_fk
    foreign key (student_id) references students(id) on delete restrict;
alter table applications add constraint applications_drive_fk
    foreign key (drive_id) references drives(id) on delete restrict;
alter table drives add constraint drives_recruiter_fk
    foreign key (recruiter_id) references recruiters(id) on delete restrict;
alter table schedule_events add constraint schedule_events_drive_fk
    foreign key (drive_id) references drives(id) on delete restrict;

-- 4. AI/analytics satellite student references --------------------------------

alter table skill_evidence      add constraint skill_evidence_student_fk      foreign key (student_id) references students(id) on delete cascade;
alter table readiness_snapshots add constraint readiness_snapshots_student_fk foreign key (student_id) references students(id) on delete cascade;
alter table risk_entries        add constraint risk_entries_student_fk        foreign key (student_id) references students(id) on delete cascade;
alter table intervention_plans  add constraint intervention_plans_student_fk  foreign key (student_id) references students(id) on delete cascade;
alter table student_skills      add constraint student_skills_student_fk      foreign key (student_id) references students(id) on delete cascade;
alter table student_goals       add constraint student_goals_student_fk       foreign key (student_id) references students(id) on delete cascade;
alter table resume_analysis     add constraint resume_analysis_student_fk     foreign key (student_id) references students(id) on delete cascade;
alter table career_roadmap      add constraint career_roadmap_student_fk      foreign key (student_id) references students(id) on delete cascade;

-- 5. Notification recipient -> account ----------------------------------------

alter table notifications add constraint notifications_recipient_fk
    foreign key (recipient_username) references user_accounts(username) on delete cascade;

