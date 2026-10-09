create table documents (
    id varchar(64) primary key,
    campus_id varchar(64) not null,
    student_id varchar(64) not null,
    type varchar(32) not null,
    file_name varchar(255) not null,
    content_type varchar(128) not null,
    size_bytes bigint not null,
    storage_key varchar(512) not null,
    scan_status varchar(32) not null default 'PENDING',
    review_status varchar(32) not null default 'PENDING',
    reviewed_by varchar(100),
    reviewed_at timestamptz,
    uploaded_at timestamptz not null,
    constraint documents_student_fk foreign key (student_id) references students(id),
    constraint documents_size_positive check (size_bytes > 0)
);

create index idx_documents_campus on documents(campus_id);
create index idx_documents_student on documents(student_id);

insert into documents (
    id, campus_id, student_id, type, file_name, content_type, size_bytes,
    storage_key, scan_status, review_status, reviewed_by, reviewed_at, uploaded_at
) values (
    'doc_demo_resume', 'campus_demo', 'stu_demo', 'RESUME', 'demo-student-resume.pdf',
    'application/pdf', 245760, 'campus_demo/stu_demo/demo-student-resume.pdf',
    'CLEAN', 'APPROVED', 'officer', '2026-09-20T12:00:00Z', '2026-09-20T11:30:00Z'
);
