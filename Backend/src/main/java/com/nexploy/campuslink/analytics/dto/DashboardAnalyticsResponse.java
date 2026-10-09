package com.nexploy.campuslink.analytics.dto;

import java.util.List;

/**
 * Campus dashboard analytics, shaped to the frontend {@code CampusAnalytics} so the command
 * center / analytics screens can consume it directly. Every figure is an aggregation computed
 * from transactional tables — nothing here is stored, and no student identity leaves the layer.
 */
public record DashboardAnalyticsResponse(
    Snapshot snapshot,
    List<BranchStat> branchStats,
    List<FunnelStage> funnel,
    List<MonthCount> offerPipeline,
    List<SalaryPoint> salaryTrend,
    List<BandCount> readinessDistribution,
    List<LevelCount> riskDistribution,
    List<RecruiterEngagement> recruiterEngagement,
    List<Alert> alerts
) {

    public record Snapshot(
        long totalStudents,
        long placementReady,
        long atRisk,
        long activeDrives,
        long applications,
        long offersCount,
        long studentsPlaced,
        int placementRate,
        long recruitersCount,
        String averageCtc,
        String highestCtc
    ) {
    }

    public record BranchStat(
        String branch,
        String branchCode,
        int placementRate,
        long studentsPlaced,
        long totalStudents
    ) {
    }

    public record FunnelStage(String stage, long count) {
    }

    public record MonthCount(String month, long offers) {
    }

    public record SalaryPoint(String month, double averageLpa, double highestLpa) {
    }

    public record BandCount(String band, long count) {
    }

    public record LevelCount(String level, long count) {
    }

    public record RecruiterEngagement(String company, long drives, long offers) {
    }

    public record Alert(String id, String severity, String message) {
    }
}
