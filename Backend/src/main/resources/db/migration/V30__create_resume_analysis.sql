-- A stored record of one Resume Intelligence run for a student, written each time they analyze an
-- uploaded resume via POST /students/me/resume-intelligence/analyze. It powers the Analysis History
-- section and progress-over-time: newest-first list plus per-run detail. Only the *analysis result*
-- is stored (headline score + the structured analysis JSON) — never the raw resume file or its full
-- extracted text. The stored JSON contains only the short evidence snippets the analysis already
-- surfaces. This record is descriptive history only; it never feeds readiness, skill-gap or
-- digital-twin calculations.

create table resume_analysis (
    id varchar(64) primary key,
    student_id varchar(64) not null,
    campus_id varchar(64) not null,
    -- Original filename of the uploaded resume, for labelling a run in the history list.
    resume_name varchar(255),
    -- Deterministic headline score (0-100) at the time of this run.
    overall_score integer not null,
    -- The full structured ResumeAnalysisResponse serialized as JSON (metrics, weaknesses,
    -- skill-match, extracted categories, suggestions, recommendation, action plan). No raw resume.
    analysis_json text not null,
    created_at timestamptz not null
);

create index idx_resume_analysis_student on resume_analysis(student_id, created_at desc);
