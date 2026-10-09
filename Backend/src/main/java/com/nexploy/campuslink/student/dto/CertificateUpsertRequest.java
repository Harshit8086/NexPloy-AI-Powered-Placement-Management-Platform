package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a student certificate's metadata. On create it is submitted as multipart
 * form fields alongside the file part; on update (metadata only) it is a JSON body. {@code type} is a
 * {@link com.nexploy.campuslink.student.entity.CertificateType} name and is parsed leniently
 * server-side. The file itself is never carried here — it is a separate multipart part.
 *
 * @param title               certificate title (required)
 * @param type                certificate type enum name, e.g. {@code INTERNSHIP} (required)
 * @param issuingOrganization issuing organization (required)
 * @param description         optional free-text description
 * @param credentialId        optional credential / certificate id
 * @param verificationUrl     optional public verification link
 * @param issuerName          optional issuer person/authority name
 * @param organizationWebsite optional issuing organization website
 */
public record CertificateUpsertRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 40) String type,
    @NotBlank @Size(max = 200) String issuingOrganization,
    @Size(max = 2000) String description,
    @Size(max = 200) String credentialId,
    @Size(max = 500) String verificationUrl,
    @Size(max = 200) String issuerName,
    @Size(max = 500) String organizationWebsite
) {
}
