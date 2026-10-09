package com.nexploy.campuslink.aptitude.dto;

import java.math.BigDecimal;

/**
 * One catalog test as shown on the Assessment Center page. {@code available} is derived from the
 * bank (enough active questions to draw it); when false, {@code unavailableReason} explains why
 * and the UI disables the card. {@code lastScore} is the student's latest completed official
 * attempt of this test, if any.
 */
public record AptitudeTestResponse(
    String testId,
    String title,
    String category,
    int questionCount,
    int durationSeconds,
    String description,
    boolean available,
    String unavailableReason,
    BigDecimal lastScore
) {
}
