package com.nexploy.campuslink.matching.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Boundary to the Python ML inference service for candidate ranking. Spring computes the
 * deterministic eligibility gate and passes each candidate's {@code eligible} flag; the model
 * ranks but never decides eligibility, so it can never rank an ineligible candidate as eligible.
 */
public interface MlMatchingClient {

    MatchingResult rank(MatchingRequest request);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MatchingRequest(
        String requestId,
        String campusId,
        String placementCycleId,
        String modelPolicyVersion,
        String driveId,
        List<Candidate> candidates,
        List<String> requiredSkills
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Candidate(
        String studentId,
        boolean eligible,
        Features features,
        List<String> skills
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Features(
        Double requiredSkillCoverage,
        Double semanticSimilarity,
        Double projectRelevance,
        Double readiness,
        Double interviewScore,
        Double academics,
        Double certificationRelevance,
        Double historicalInterviewPassRate
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MatchingResult(
        String driveId,
        List<RankedCandidate> ranked,
        String modelVersion,
        String featureVersion,
        OffsetDateTime generatedAt
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RankedCandidate(
        String studentId,
        String status,
        Double fitScore,
        Integer rank,
        List<Factor> topFactors,
        double confidence,
        Double semanticSimilarity,
        List<String> warnings
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Factor(
        String name,
        double impact,
        String direction
    ) {
    }
}
