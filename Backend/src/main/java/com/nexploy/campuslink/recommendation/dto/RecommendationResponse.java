package com.nexploy.campuslink.recommendation.dto;

import com.nexploy.campuslink.drive.dto.EligibilityResponse.EligibilityCheck;
import com.nexploy.campuslink.matching.dto.MatchResponse.MatchBreakdown;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Explainable recommendation for one application. Everything except {@code aiExplanation} and
 * {@code improvementAreas} is deterministic, verified evidence (the LLM never produces or alters it):
 * the eligibility verdict + per-criterion checks, the fit score and 7-factor breakdown, and the
 * derived {@link RecommendationType}. {@code aiExplanation} is the persisted human-readable overlay and
 * is {@code null} when AI is disabled/unavailable and no fallback has been stored; {@code explanationStatus}
 * is {@code OK} (LLM) or {@code FALLBACK} (deterministic template), or {@code null} when none exists yet.
 *
 * <p>When no {@code MatchResult} could be computed (e.g. the ML ranker was unreachable), the response
 * degrades to eligibility-only: {@code overallFit}/{@code breakdown}/{@code computedAt} are {@code null}
 * and the skill lists are empty, but eligibility evidence is always present so the endpoint never errors.
 */
public record RecommendationResponse(
    String applicationId,
    String studentId,
    String driveId,
    RecommendationType recommendationType,
    boolean eligibilityPassed,
    List<EligibilityCheck> eligibilityChecks,
    Integer overallFit,
    MatchBreakdown breakdown,
    List<String> whySelected,
    List<String> weakOrMissing,
    String aiExplanation,
    List<String> improvementAreas,
    String explanationStatus,
    OffsetDateTime computedAt
) {
}
