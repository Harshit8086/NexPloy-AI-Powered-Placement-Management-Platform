package com.nexploy.campuslink.aptitude.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full attempt view: GET /attempts/{id}, the POST /attempts start result, and the POST
 * /attempts/{id}/submit result all serialize this shape. Scoring fields ({@code score},
 * {@code correctCount}, {@code sections}, per-question correctness) are populated only once the
 * attempt is COMPLETED.
 */
public record AttemptResponse(
    String attemptId,
    String testId,
    String testTitle,
    String mode,
    String category,
    String status,
    int totalQuestions,
    int durationLimitSeconds,
    Integer timeSpentSeconds,
    BigDecimal score,
    Integer correctCount,
    BigDecimal aptitudeBefore,
    OffsetDateTime startedAt,
    OffsetDateTime submittedAt,
    List<AttemptQuestionResponse> questions,
    List<SectionResult> sections
) {

    /** Category-wise deterministic breakdown shown on the result page. */
    public record SectionResult(
        String category,
        int total,
        int correct,
        BigDecimal percent
    ) {
    }
}
