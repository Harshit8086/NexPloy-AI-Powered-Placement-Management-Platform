package com.nexploy.campuslink.risk.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Frontend-facing view of a persisted risk entry (camelCase). The service parses the stored
 * factor/intervention JSON columns and passes the lists in.
 */
public record RiskEntryResponse(
    String id,
    String studentId,
    String placementCycleId,
    String level,
    double supportProbability,
    String primaryReason,
    List<RiskFactorView> factors,
    List<String> recommendedInterventions,
    double confidence,
    String modelVersion,
    String featureVersion,
    OffsetDateTime generatedAt
) {

    /** A driving factor as stored (mirrors the ML client Factor / frontend RiskFactor). */
    public record RiskFactorView(String name, double impact, String direction) {
    }
}
