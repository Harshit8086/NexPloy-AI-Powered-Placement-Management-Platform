package com.nexploy.campuslink.recruiter.dto;

import java.util.List;

/**
 * Recruiter/company profile as served to the frontend. All fields come from persisted data:
 * the base identity + company columns from the {@code recruiters} row, and the two derived
 * numbers ({@code activeDriveIds}, {@code studentsHired}) computed from the recruiter's real
 * drives and offers by {@code RecruiterService}. Fields the backend does not store are simply
 * absent here — the frontend renders an empty state rather than inventing a value.
 */
public record RecruiterResponse(
    String id,
    String campusId,
    String name,
    String companyName,
    String email,
    boolean active,
    String phone,
    String companyWebsite,
    String companyIndustry,
    String companyLocation,
    String designation,
    String logoInitial,
    String companyDescription,
    String companySize,
    String companyLinkedin,
    String companyTwitter,
    String companyGithub,
    List<String> activeDriveIds,
    int studentsHired
) {
}
