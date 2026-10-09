package com.nexploy.campuslink.recruiter.dto;

import jakarta.validation.constraints.Size;

/**
 * Recruiter self-service profile edit (PUT /api/recruiters/me). Every field is optional: a null
 * means "leave unchanged", so the client may send a partial patch. Only fields a recruiter
 * legitimately self-reports are here — identity/scope fields (id, email, campusId, active) and the
 * derived numbers (activeDriveIds, studentsHired) are deliberately NOT editable and are ignored
 * even if sent. Sizes mirror the recruiters table column widths.
 */
public record RecruiterUpdateRequest(
    // Account
    @Size(max = 200) String name,
    @Size(max = 32) String phone,
    @Size(max = 120) String designation,
    @Size(max = 4) String logoInitial,

    // Company
    @Size(max = 200) String companyName,
    @Size(max = 120) String companyIndustry,
    @Size(max = 320) String companyWebsite,
    @Size(max = 200) String companyLocation,
    @Size(max = 4000) String companyDescription,
    @Size(max = 40) String companySize,
    @Size(max = 320) String companyLinkedin,
    @Size(max = 320) String companyTwitter,
    @Size(max = 320) String companyGithub
) {
}
