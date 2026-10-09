package com.nexploy.campuslink.student.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One recommended live drive for the signed-in student, with a deterministic 0-100 {@code matchScore}
 * and the explainable signals behind it. Everything here is derived from real data — the student's
 * own recorded skills/strengths/preferences and the drive's stored criteria — never fabricated. When
 * the student's campus has no open drives the recommendation list is simply empty.
 */
public record CareerRecommendationResponse(
    String driveId,
    String companyName,
    String role,
    String location,
    BigDecimal packageLpa,
    int matchScore,
    boolean eligible,
    List<String> matchedSkills,
    List<String> missingSkills,
    List<String> reasons
) {
}
