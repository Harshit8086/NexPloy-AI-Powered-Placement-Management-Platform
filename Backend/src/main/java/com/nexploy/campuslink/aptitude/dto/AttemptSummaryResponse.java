package com.nexploy.campuslink.aptitude.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One row of the student's attempt history (GET /history). */
public record AttemptSummaryResponse(
    String attemptId,
    String testId,
    String testTitle,
    String mode,
    String category,
    String status,
    int totalQuestions,
    Integer correctCount,
    BigDecimal score,
    OffsetDateTime startedAt,
    OffsetDateTime submittedAt
) {
}
