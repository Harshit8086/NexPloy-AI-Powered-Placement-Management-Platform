package com.nexploy.campuslink.analytics.dto;

import java.util.List;

/**
 * Institute-wide rollup across every campus the caller may access (all campuses for OFFICER/ADMIN,
 * the caller's own campus otherwise). Every figure is a live aggregation over the same
 * students/drives/offers used by the per-campus dashboard — nothing is fabricated. When the caller
 * can see no campuses (or they hold no data), {@code totals} is all-zero and {@code campuses} is empty.
 */
public record InstituteAnalyticsResponse(Totals totals, List<CampusComparison> campuses) {

    public record Totals(
        long totalStudents,
        long placementReady,
        long placed,
        long atRisk,
        long offers,
        long activeDrives,
        long recruiters,
        int placementRate) {}

    public record CampusComparison(
        String campusId,
        String campus,
        String city,
        long students,
        long ready,
        long placed,
        int placementRate,
        String averageCtc) {}
}
