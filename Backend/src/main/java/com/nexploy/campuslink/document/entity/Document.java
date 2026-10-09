package com.nexploy.campuslink.document.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata for a stored document. The file bytes live in the sibling {@code document_files}
 * table ({@link DocumentFile}, shared primary key) so listings never load the blob; this row
 * keeps the display name, content type, size, scan status, and review decision. Legacy rows
 * may predate file storage and have no bytes attached ({@code hasFile=false} on the response).
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    private String id;

    private String campusId;
    private String studentId;

    @Enumerated(EnumType.STRING)
    private DocumentType type;

    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String storageKey;

    @Enumerated(EnumType.STRING)
    private ScanStatus scanStatus;

    @Enumerated(EnumType.STRING)
    private ReviewStatus reviewStatus;

    private String reviewedBy;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime uploadedAt;
}
