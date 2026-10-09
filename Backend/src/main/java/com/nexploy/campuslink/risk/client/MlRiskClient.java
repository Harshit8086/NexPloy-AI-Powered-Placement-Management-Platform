package com.nexploy.campuslink.risk.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Boundary to the Python ML inference service for placement-risk prediction. The result is
 * support-oriented: Spring uses it to offer help, never to exclude a student.
 */
public interface MlRiskClient {

    RiskPrediction predict(RiskPredictRequest request);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RiskPredictRequest(
        String requestId,
        String campusId,
        String placementCycleId,
        String modelPolicyVersion,
        String studentId,
        Signals signals
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Signals(
        Double readinessScore,
        Integer backlogCount,
        Integer applicationsSubmitted,
        Integer interviewsAttended,
        Integer interviewsMissed,
        Integer daysSinceLastActivity,
        Integer mockInterviewsCompleted
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RiskPrediction(
        String studentId,
        double supportProbability,
        String band,
        List<Factor> topFactors,
        List<String> recommendedInterventions,
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
