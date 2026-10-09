package com.nexploy.campuslink.student.dto;

import com.nexploy.campuslink.student.entity.CertificateType;
import com.nexploy.campuslink.student.entity.StudentCertificate;
import java.time.OffsetDateTime;

/**
 * A student certificate as returned to the client. Carries the metadata and the uploaded file's
 * description (name/type/size) — never the bytes; the file is fetched from the dedicated
 * {@code /file} endpoint. {@code type} is the {@link CertificateType} name.
 */
public record StudentCertificateResponse(
    String id,
    String title,
    String type,
    String issuingOrganization,
    String description,
    String credentialId,
    String verificationUrl,
    String issuerName,
    String organizationWebsite,
    String fileName,
    String contentType,
    Long fileSizeBytes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static StudentCertificateResponse from(StudentCertificate c) {
        return new StudentCertificateResponse(
            c.getId(),
            c.getTitle(),
            c.getType() == null ? CertificateType.OTHER.name() : c.getType().name(),
            c.getIssuingOrganization(),
            c.getDescription(),
            c.getCredentialId(),
            c.getVerificationUrl(),
            c.getIssuerName(),
            c.getOrganizationWebsite(),
            c.getFileName(),
            c.getContentType(),
            c.getFileSizeBytes(),
            c.getCreatedAt(),
            c.getUpdatedAt()
        );
    }
}
