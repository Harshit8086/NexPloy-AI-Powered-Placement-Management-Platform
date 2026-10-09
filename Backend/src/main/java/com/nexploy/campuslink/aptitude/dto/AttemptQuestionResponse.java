package com.nexploy.campuslink.aptitude.dto;

/**
 * One question inside an attempt. {@code correctOption} and {@code explanation} are null while the
 * attempt is in progress — they are only serialized once the attempt is completed, so an
 * in-progress attempt can never leak the answer key. {@code category} is never answer-sensitive
 * and is serialized in both states.
 */
public record AttemptQuestionResponse(
    String attemptQuestionId,
    int ordinal,
    String category,
    String prompt,
    String optionA,
    String optionB,
    String optionC,
    String optionD,
    String selectedOption,
    boolean marked,
    Boolean correct,
    String correctOption,
    String explanation
) {
}
