package com.nexploy.campuslink.readiness.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Boundary to the Python ML inference service for readiness scoring. Spring remains the
 * system of record; this client only requests a versioned, explainable prediction.
 */
public interface MlReadinessClient {

    ReadinessScore score(ReadinessScoreRequest request);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ReadinessScoreRequest(
        String requestId,
        String campusId,
        String placementCycleId,
        String modelPolicyVersion,
        String studentId,
        Assessments assessments
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Assessments(
        Double academic,
        Double aptitude,
        Double communication,
        Double projects,
        Double interview
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ReadinessScore(
        String studentId,
        double score,
        String category,
        java.util.Map<String, Double> breakdown,
        List<Factor> topFactors,
        double confidence,
        List<String> warnings,
        String modelVersion,
        String featureVersion,
        OffsetDateTime generatedAt
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
