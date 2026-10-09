package com.nexploy.campuslink.recommendation.dto;

import java.util.List;

/**
 * One ranked candidate in a drive's recommendation list. Order is the deterministic fit ranking; the
 * LLM never reorders. Serves only the persisted AI explanation — {@code aiExplanation} is {@code null}
 * until it has been generated (via the per-application endpoint or an explicit regenerate), so this
 * list view never triggers an LLM call.
 */
public record CandidateRecommendationResponse(
    String studentId,
    int overallFit,
    RecommendationType recommendationType,
    boolean eligibilityPassed,
    List<String> whySelected,
    List<String> weakOrMissing,
    String aiExplanation,
    String explanationStatus
) {
}
