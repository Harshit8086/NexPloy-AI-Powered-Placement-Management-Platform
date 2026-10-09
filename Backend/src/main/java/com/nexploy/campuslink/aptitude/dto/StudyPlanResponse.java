package com.nexploy.campuslink.aptitude.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deterministic study plan for one completed attempt: one curated recommendation per weak section,
 * weakest first. Content comes from the version-controlled study catalog — no AI is involved, so a
 * recommendation can never point at a fabricated topic or URL.
 */
public record StudyPlanResponse(
    String attemptId,
    List<Recommendation> recommendations) {

    /**
     * One weak section's study guidance. {@code percent} is the section score; {@code priority} is
     * HIGH below 50%, MEDIUM otherwise.
     */
    public record Recommendation(
        String category,
        BigDecimal percent,
        String priority,
        List<String> topics,
        List<Resource> resources,
        String practiceTask) {}

    /** One curated resource link. */
    public record Resource(
        String title,
        String provider,
        String url,
        String type) {}
}