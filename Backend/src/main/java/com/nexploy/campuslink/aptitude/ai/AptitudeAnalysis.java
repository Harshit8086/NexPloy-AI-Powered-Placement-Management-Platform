package com.nexploy.campuslink.aptitude.ai;

import java.util.List;

/** AI output for the performance-analysis page. */
public record AptitudeAnalysis(
    String summary,
    List<String> strengths,
    List<String> weaknesses,
    List<String> tips
) {
}
