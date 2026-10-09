package com.nexploy.campuslink.student.entity;

/**
 * Kind of certificate a student showcases. Persisted by name as a {@code varchar(32)} via
 * {@code @Enumerated(EnumType.STRING)} on {@link StudentCertificate}, mirroring the codebase's other
 * classification enums (e.g. {@code DocumentType}, {@code AssessmentType}). {@link #fromString(String)}
 * is a lenient, null-safe parse so an unknown/blank label resolves to {@link #OTHER} rather than
 * throwing — imports and forms never fail on a stray value.
 */
public enum CertificateType {
    COURSE_COMPLETION,
    INTERNSHIP,
    WORKSHOP,
    HACKATHON,
    COMPETITION,
    ACHIEVEMENT,
    WEBINAR,
    ACADEMIC,
    PROFESSIONAL_CERTIFICATION,
    OTHER;

    /** Case-insensitive parse; null/blank/unknown → {@link #OTHER} (never throws). */
    public static CertificateType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        try {
            return CertificateType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return OTHER;
        }
    }
}
