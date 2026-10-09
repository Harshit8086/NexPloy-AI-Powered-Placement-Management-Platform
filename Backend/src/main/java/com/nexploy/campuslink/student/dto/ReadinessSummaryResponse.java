package com.nexploy.campuslink.student.dto;

/**
 * Read-only readiness summary for the signed-in student: the overall score and its five-factor
 * breakdown. Both are derived from the student's stored {@code readinessScore} + {@code breakdownJson}
 * — never recomputed or fabricated here (assessment/derived scores stay authoritative).
 */
public record ReadinessSummaryResponse(Integer score, ReadinessBreakdown breakdown) {
}
