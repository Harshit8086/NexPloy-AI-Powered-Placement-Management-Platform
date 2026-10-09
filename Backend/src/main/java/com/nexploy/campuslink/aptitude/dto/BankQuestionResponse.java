package com.nexploy.campuslink.aptitude.dto;

import java.time.OffsetDateTime;

/** One bank question as managed on the officer's Question Bank page. */
public record BankQuestionResponse(
    String id,
    String category,
    String difficulty,
    String prompt,
    String optionA,
    String optionB,
    String optionC,
    String optionD,
    String correctOption,
    String explanation,
    String source,
    boolean active,
    OffsetDateTime createdAt
) {
}
