-- V43: Role-skill requirements catalog.
--
-- Provides the required proficiency levels (and importance weights) a target role expects, so the
-- skill-gap analysis can compare a student's stored skills against real, data-backed targets rather
-- than levels supplied/hardcoded by the client. Additive and reversible: a new table plus seed rows,
-- no change to existing tables.
--
-- campus_id is nullable: a NULL row is a GLOBAL default applied to every campus; a campus-scoped row
-- overrides the global one for that campus. Case-insensitive uniqueness (role_name, skill) is enforced
-- in the repository, matching how student_skills avoids duplicates, so no lower() functional index is
-- needed here (keeps the schema H2-compatible for the integration tests).

create table if not exists role_skill_requirements (
    id             varchar(64)   primary key,
    campus_id      varchar(64),
    role_name      varchar(200)  not null,
    skill          varchar(120)  not null,
    required_level integer       not null,
    weight         integer       not null default 1,
    updated_at     timestamptz   not null default now()
);

create unique index if not exists ux_role_skill_req_scope
    on role_skill_requirements (campus_id, role_name, skill);

create index if not exists ix_role_skill_req_role
    on role_skill_requirements (role_name);

-- Global default catalog (campus_id NULL) for canonical demo roles present in the seed data.
insert into role_skill_requirements (id, campus_id, role_name, skill, required_level, weight) values
    ('rsr_fsd_java',      null, 'Full Stack Developer', 'Java',          70, 3),
    ('rsr_fsd_spring',    null, 'Full Stack Developer', 'Spring Boot',   70, 3),
    ('rsr_fsd_react',     null, 'Full Stack Developer', 'React',         70, 3),
    ('rsr_fsd_js',        null, 'Full Stack Developer', 'JavaScript',    65, 2),
    ('rsr_fsd_sql',       null, 'Full Stack Developer', 'SQL',           60, 2),
    ('rsr_fsd_rest',      null, 'Full Stack Developer', 'REST APIs',     65, 2),
    ('rsr_fsd_git',       null, 'Full Stack Developer', 'Git',           50, 1),
    ('rsr_fsd_sysdes',    null, 'Full Stack Developer', 'System Design', 55, 1);

insert into role_skill_requirements (id, campus_id, role_name, skill, required_level, weight) values
    ('rsr_bd_java',    null, 'Backend Developer', 'Java',          75, 3),
    ('rsr_bd_spring',  null, 'Backend Developer', 'Spring Boot',   75, 3),
    ('rsr_bd_sql',     null, 'Backend Developer', 'SQL',           70, 2),
    ('rsr_bd_rest',    null, 'Backend Developer', 'REST APIs',     70, 2),
    ('rsr_bd_sysdes',  null, 'Backend Developer', 'System Design', 60, 2),
    ('rsr_bd_micro',   null, 'Backend Developer', 'Microservices', 60, 2),
    ('rsr_bd_docker',  null, 'Backend Developer', 'Docker',        55, 1),
    ('rsr_bd_git',     null, 'Backend Developer', 'Git',           50, 1);

insert into role_skill_requirements (id, campus_id, role_name, skill, required_level, weight) values
    ('rsr_frd_js',     null, 'Frontend Developer', 'JavaScript', 75, 3),
    ('rsr_frd_react',  null, 'Frontend Developer', 'React',      75, 3),
    ('rsr_frd_ts',     null, 'Frontend Developer', 'TypeScript', 60, 2),
    ('rsr_frd_html',   null, 'Frontend Developer', 'HTML',       65, 2),
    ('rsr_frd_css',    null, 'Frontend Developer', 'CSS',        65, 2),
    ('rsr_frd_rest',   null, 'Frontend Developer', 'REST APIs',  55, 1),
    ('rsr_frd_git',    null, 'Frontend Developer', 'Git',        50, 1);

insert into role_skill_requirements (id, campus_id, role_name, skill, required_level, weight) values
    ('rsr_da_sql',     null, 'Data Analyst', 'SQL',                75, 3),
    ('rsr_da_python',  null, 'Data Analyst', 'Python',             70, 3),
    ('rsr_da_stats',   null, 'Data Analyst', 'Statistics',         60, 2),
    ('rsr_da_viz',     null, 'Data Analyst', 'Data Visualization', 65, 2),
    ('rsr_da_excel',   null, 'Data Analyst', 'Excel',              60, 2),
    ('rsr_da_pbi',     null, 'Data Analyst', 'Power BI',           55, 1);

insert into role_skill_requirements (id, campus_id, role_name, skill, required_level, weight) values
    ('rsr_ce_aws',     null, 'Cloud Engineer', 'AWS',        70, 3),
    ('rsr_ce_docker',  null, 'Cloud Engineer', 'Docker',     65, 2),
    ('rsr_ce_k8s',     null, 'Cloud Engineer', 'Kubernetes', 65, 2),
    ('rsr_ce_linux',   null, 'Cloud Engineer', 'Linux',      60, 2),
    ('rsr_ce_cicd',    null, 'Cloud Engineer', 'CI/CD',      60, 2),
    ('rsr_ce_tf',      null, 'Cloud Engineer', 'Terraform',  55, 1),
    ('rsr_ce_python',  null, 'Cloud Engineer', 'Python',     55, 1);

insert into role_skill_requirements (id, campus_id, role_name, skill, required_level, weight) values
    ('rsr_se_ds',      null, 'Software Engineer', 'Data Structures', 70, 3),
    ('rsr_se_algo',    null, 'Software Engineer', 'Algorithms',      70, 3),
    ('rsr_se_java',    null, 'Software Engineer', 'Java',            60, 2),
    ('rsr_se_sysdes',  null, 'Software Engineer', 'System Design',   55, 2),
    ('rsr_se_sql',     null, 'Software Engineer', 'SQL',             55, 1),
    ('rsr_se_git',     null, 'Software Engineer', 'Git',             50, 1);
