package com.nexploy.campuslink.analytics.dto;

import java.util.Map;

/**
 * Read-only, campus-scoped placement rollup for dashboards. Aggregate counts only: it carries no
 * student identity and drives no decision. Breakdowns are keyed by status name so the frontend
 * can render them without knowing the enum set in advance.
 */
public record PlacementSummaryResponse(
    String campusId,
    long studentCount,
    long placedStudentCount,
    double placementRate,
    Double averageReadinessScore,
    Map<String, Long> studentsByPlacementStatus,
    long driveCount,
    Map<String, Long> drivesByStatus,
    long offerCount,
    Map<String, Long> offersByStatus,
    double offerAcceptanceRate,
    long applicationCount,
    Map<String, Long> applicationsByStatus
) {
}
