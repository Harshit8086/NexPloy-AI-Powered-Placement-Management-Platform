package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Column;
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
 * A certificate a student adds to showcase on their profile (course completion, internship, hackathon,
 * …). This row holds only the metadata + a description of the uploaded file; the file bytes live in a
 * separate {@link StudentCertificateFile} row (keyed by the same id) so listing certificates never
 * pulls the binary. The student's own, freely-editable record — one row per certificate.
 *
 * <p>Kept deliberately separate from the free-text {@code certificationsJson} name chips on
 * {@link Student}: those are lightweight tags; this is a structured, file-backed credential.
 */
@Entity
@Table(name = "student_certificates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCertificate {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** Certificate title, e.g. "AWS Certified Cloud Practitioner". Required. */
    private String title;

    @Enumerated(EnumType.STRING)
    private CertificateType type;

    /** Issuing organization, e.g. "Amazon Web Services". Required. */
    private String issuingOrganization;

    /** Optional free-text description. */
    @Column(columnDefinition = "text")
    private String description;

    /** Optional credential / certificate id shown on the card. */
    private String credentialId;

    /** Optional public verification link. */
    private String verificationUrl;

    /** Optional issuer person/authority name. */
    private String issuerName;

    /** Optional issuing organization website. */
    private String organizationWebsite;

    /** Uploaded file metadata (the bytes are in {@link StudentCertificateFile}). */
    private String fileName;
    private String contentType;
    private Long fileSizeBytes;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
