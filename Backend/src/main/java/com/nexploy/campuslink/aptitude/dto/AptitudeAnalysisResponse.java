package com.nexploy.campuslink.aptitude.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI performance analysis of a completed attempt (GET /attempts/{id}/analysis). {@code aptitudeBefore}
 * is the snapshot captured at submit time; {@code aptitudeAfter} is the student's current
 * assessment_aptitude — the readiness before/after card on the analysis page.
 */
public record AptitudeAnalysisResponse(
    String attemptId,
    String testTitle,
    BigDecimal score,
    String summary,
    List<String> strengths,
    List<String> weaknesses,
    List<String> tips,
    BigDecimal aptitudeBefore,
    BigDecimal aptitudeAfter
) {
}
