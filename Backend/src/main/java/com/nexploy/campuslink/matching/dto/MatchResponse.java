package com.nexploy.campuslink.matching.dto;

import java.util.List;

/**
 * Frontend-facing, explainable candidate match (camelCase). Maps 1:1 to the frontend
 * {@code CandidateMatch}: an overall fit, a nine-part breakdown, the reasons for and against,
 * and a recommendation label. This is the display view over the ML ranker's output — the raw
 * ML response is snake_case and factor-shaped; this DTO is what the UI renders.
 *
 * <p>{@code breakdown.eligibility} is a contribution-weight display (100 when the deterministic
 * gate passed, 0 otherwise), never the eligibility verdict itself. {@code breakdown.semanticMatch}
 * is currently a keyword-overlap proxy equal to {@code skills}; it will be replaced by embedding
 * cosine similarity when vector search lands (see CAMPUSLINK_SOLUTION_PLAN §3a).
 * {@code breakdown.certificationRelevance} is the share of the drive's required skills backed by
 * one of the student's certifications (name containment, case-insensitive); 0 when no certifications
 * are on record, so it never raises eligibility and only ever lifts the merit ranking.
 * {@code breakdown.pastInterviews} is the pass rate over the student's completed past interviews
 * (SELECTED vs REJECTED), excluding the current drive; 0 when none are on record. Merit only —
 * it never gates eligibility.
 */
public record MatchResponse(
    String id,
    String studentId,
    String driveId,
    int overallFit,
    MatchBreakdown breakdown,
    List<String> whySelected,
    List<String> weakOrMissing,
    String recommendation
) {

    public record MatchBreakdown(
        int eligibility,
        int skills,
        int semanticMatch,
        int projects,
        int interview,
        int readiness,
        int academics,
        int certificationRelevance,
        int pastInterviews
    ) {
    }
}
