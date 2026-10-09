package com.nexploy.campuslink.aptitude.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body of PUT /attempts/{id}/answers — persists one question's state so a page refresh resumes the
 * attempt exactly where it left off. A null {@code selectedOption} clears the answer (the question
 * returns to "seen/unanswered"); {@code marked} mirrors the mark-for-review toggle.
 */
public record SaveAnswerRequest(
    @NotBlank String attemptQuestionId,
    @Pattern(regexp = "[A-D]") String selectedOption,
    boolean marked
) {
}
