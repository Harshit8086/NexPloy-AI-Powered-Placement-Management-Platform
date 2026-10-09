-- Student showcase certificates — a NEW self-service capability. Each row is a certificate a student
-- adds to their profile (metadata). The uploaded document's bytes live in a sibling
-- student_certificate_files row (shared id) so listing/showcase never loads the payload; the bytes are
-- streamed back only through the owner-checked download endpoint. Kept separate from the free-text
-- certification NAME chips cached in students.certifications_json, which are unchanged.
--
-- Postgres-only. The H2 "test" profile runs with Flyway disabled and derives the schema from the JPA
-- entities (StudentCertificate, StudentCertificateFile), so column names/types here mirror the entities
-- (Hibernate's default camelCase -> snake_case naming; bytes as bytea, matching the VARBINARY mapping).
-- Prod runs ddl-auto: validate.

CREATE TABLE student_certificates (
    id                    varchar(64)  PRIMARY KEY,
    student_id            varchar(64)  NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    campus_id             varchar(64),                       -- normally the student's campus
    title                 varchar(255) NOT NULL,
    type                  varchar(32)  NOT NULL,             -- CertificateType name (COURSE_COMPLETION, INTERNSHIP, ...)
    issuing_organization  varchar(255) NOT NULL,
    description           text,
    credential_id         varchar(255),
    verification_url      varchar(500),
    issuer_name           varchar(255),
    organization_website  varchar(500),
    file_name             varchar(255),                      -- uploaded document metadata (bytes in student_certificate_files)
    content_type          varchar(100),
    file_size_bytes       bigint,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL
);

CREATE INDEX idx_student_certificates_student ON student_certificates(student_id, created_at DESC);
CREATE INDEX idx_student_certificates_campus ON student_certificates(campus_id);

-- Bytes of the uploaded document, kept out of the metadata table so the listing query never loads them.
CREATE TABLE student_certificate_files (
    id            varchar(64) PRIMARY KEY REFERENCES student_certificates(id) ON DELETE CASCADE,
    content       bytea       NOT NULL,
    content_type  varchar(100),
    size_bytes    bigint
);
