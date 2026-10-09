package com.nexploy.campuslink.matching.dto;

import java.util.List;

/**
 * Request for the batch auto-shortlist action. {@code minFit} is the inclusive match-fit floor
 * (0-100; when null the service applies its default of 75). {@code studentIds}, when non-null,
 * restricts the run to that subset — every other scored applicant is left untouched; when null the
 * whole scored pool for the drive is considered. Eligibility is always re-verified server-side, so
 * an ineligible student is never shortlisted regardless of what the caller supplies.
 */
public record AutoShortlistRequest(Integer minFit, List<String> studentIds) {
}
