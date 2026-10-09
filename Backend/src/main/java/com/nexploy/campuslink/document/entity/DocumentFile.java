package com.nexploy.campuslink.document.entity;

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
 * The binary bytes of a student's submitted document, stored in its own table so the documents
 * roster never loads the blob — only the file endpoint fetches this row. Its id equals the owning
 * {@link Document} id (a shared primary key); the row is removed with its document via the
 * {@code ON DELETE CASCADE} foreign key from the V66 migration.
 *
 * <p>The bytes use an explicit {@code bytea} column: it is portable across real Postgres and H2's
 * PostgreSQL compatibility mode (the test profile), unlike {@code @Lob}, which generates a
 * {@code BLOB} that H2's PG mode rejects and that {@code PostgreSQLDialect} would render as
 * {@code oid} — either way mismatching the {@code bytea} column created by V66.
 */
@Entity
@Table(name = "document_files")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFile {

    /** Same value as the owning {@link Document#getId()} (shared primary key). */
    @Id
    private String id;

    @Column(columnDefinition = "bytea", nullable = false)
    private byte[] content;

    private String contentType;
    private Long sizeBytes;
}
