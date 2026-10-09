package com.nexploy.campuslink.analytics.controller;

import com.nexploy.campuslink.analytics.dto.InstituteAnalyticsResponse;
import com.nexploy.campuslink.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Institute-wide analytics that span more than one campus, so they are not nested under a single
 * {@code /campuses/{campusId}} path. Scoping (which campuses the caller may roll up) is enforced
 * inside the service against the authenticated user's role and campus.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class InstituteAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/institute")
    public InstituteAnalyticsResponse institute() {
        return analyticsService.institute();
    }
}
