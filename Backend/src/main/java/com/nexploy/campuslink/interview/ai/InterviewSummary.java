package com.nexploy.campuslink.interview.ai;

import java.util.List;

/**
 * The final report for a completed practice interview: a 0-100 overall score, a narrative summary,
 * and grounded strengths / weaknesses / recommendations. Descriptive only.
 */
public record InterviewSummary(
    int overallScore,
    String summary,
    List<String> strengths,
    List<String> weaknesses,
    List<String> recommendations
) {
}
