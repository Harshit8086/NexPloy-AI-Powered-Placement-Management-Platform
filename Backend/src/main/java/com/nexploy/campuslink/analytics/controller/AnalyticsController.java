package com.nexploy.campuslink.analytics.controller;

import com.nexploy.campuslink.analytics.dto.DashboardAnalyticsResponse;
import com.nexploy.campuslink.analytics.dto.PlacementSummaryResponse;
import com.nexploy.campuslink.analytics.dto.SkillAnalyticsResponse;
import com.nexploy.campuslink.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campuses/{campusId}/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/placement-summary")
    public PlacementSummaryResponse placementSummary(@PathVariable String campusId) {
        return analyticsService.placementSummary(campusId);
    }

    /** Full dashboard bundle (snapshot + chart series) for the command-center screens. */
    @GetMapping("/dashboard")
    public DashboardAnalyticsResponse dashboard(@PathVariable String campusId) {
        return analyticsService.dashboard(campusId);
    }

    /** Skill supply/demand for this campus (active-drive demand vs cohort coverage). */
    @GetMapping("/skills")
    public SkillAnalyticsResponse skills(@PathVariable String campusId) {
        return analyticsService.skillAnalytics(campusId);
    }
}
