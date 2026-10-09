package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The binary bytes of a student's certificate file, stored in its own table so a certificate listing
 * never loads the blob — only the "view file" endpoint fetches this row. Its id equals the owning
 * {@link StudentCertificate} id (a shared primary key); the row is removed with its certificate via the
 * {@code ON DELETE CASCADE} foreign key.
 *
 * <p>The bytes use an explicit {@code bytea} column: it is portable across real Postgres and H2's
 * PostgreSQL compatibility mode (the test profile), unlike {@code @Lob}, which generates a {@code BLOB}
 * that H2's PG mode rejects and that {@code PostgreSQLDialect} would render as {@code oid} — either way
 * mismatching the {@code bytea} column created by the V55 migration.
 */
@Entity
@Table(name = "student_certificate_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCertificateFile {

    /** Same value as the owning {@link StudentCertificate#getId()} (shared primary key). */
    @Id
    private String id;

    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] content;

    private String contentType;
    private Long sizeBytes;
}
