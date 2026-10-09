package com.nexploy.campuslink.aptitude.ai;

import java.util.List;

/**
 * Deterministic summary of a completed attempt handed to the AI for analysis. The AI never sees
 * raw questions or answers — only these totals — so it cannot leak or hallucinate content.
 */
public record AptitudeAttemptStats(
    String testTitle,
    int totalQuestions,
    int correctCount,
    int timeSpentSeconds,
    int durationLimitSeconds,
    List<CategoryStats> categories
) {

    /** Per-category totals for the section-wise breakdown. */
    public record CategoryStats(
        String category,
        int total,
        int correct
    ) {
    }
}
