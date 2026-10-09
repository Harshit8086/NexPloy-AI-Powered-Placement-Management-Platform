package com.nexploy.campuslink.document.dto;

import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.DocumentType;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.entity.ScanStatus;
import java.time.OffsetDateTime;

/**
 * Wire shape of a document row. {@code hasFile} tells the client whether real bytes are
 * attached ({@code document_files} row exists) — legacy metadata-only registrations have
 * none, so the officer UI disables preview for them and the student sees a re-upload affordance.
 */
public record DocumentResponse(
    String id,
    String campusId,
    String studentId,
    DocumentType type,
    String fileName,
    String contentType,
    long sizeBytes,
    ScanStatus scanStatus,
    ReviewStatus reviewStatus,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime uploadedAt,
    boolean hasFile
) {

    public static DocumentResponse from(Document document, boolean hasFile) {
        return new DocumentResponse(
            document.getId(),
            document.getCampusId(),
            document.getStudentId(),
            document.getType(),
            document.getFileName(),
            document.getContentType(),
            document.getSizeBytes(),
            document.getScanStatus(),
            document.getReviewStatus(),
            document.getReviewedBy(),
            document.getReviewedAt(),
            document.getUploadedAt(),
            hasFile
        );
    }
}
