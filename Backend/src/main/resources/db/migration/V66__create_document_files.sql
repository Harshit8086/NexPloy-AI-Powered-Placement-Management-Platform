-- File bytes for student documents. Mirrors student_certificate_files (V55): a shared-PK sibling
-- table so the documents roster never touches the blob — only the file endpoint loads this row,
-- and the row disappears with its document via ON DELETE CASCADE. Column names must stay in sync
-- with the DocumentFile entity (Hibernate snake_case): production boots with ddl-auto: validate.
CREATE TABLE document_files (
    id           varchar(64) PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
    content      bytea       NOT NULL,
    content_type varchar(128),
    size_bytes   bigint
);
