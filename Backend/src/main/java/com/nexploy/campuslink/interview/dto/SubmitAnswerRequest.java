package com.nexploy.campuslink.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/students/me/mock-interviews/{id}/answers} — the student's answer
 * to the current question. A blank answer is allowed (it simply scores low); the size cap bounds the
 * text sent to the model.
 */
public record SubmitAnswerRequest(
    @NotNull @Size(max = 20000) String answer
) {
}
