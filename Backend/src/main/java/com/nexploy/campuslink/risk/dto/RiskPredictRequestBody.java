package com.nexploy.campuslink.risk.dto;

import jakarta.validation.constraints.PositiveOrZero;

/**
 * Optional overrides for behavioural placement-activity signals. When omitted, the risk service
 * derives them from the student's saved applications, interviews, and mock interviews. Readiness
 * score and backlog count are always read from the student record.
 */
public record RiskPredictRequestBody(
    @PositiveOrZero Integer applicationsSubmitted,
    @PositiveOrZero Integer interviewsAttended,
    @PositiveOrZero Integer interviewsMissed,
    @PositiveOrZero Integer daysSinceLastActivity,
    @PositiveOrZero Integer mockInterviewsCompleted
) {
}
