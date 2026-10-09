package com.nexploy.campuslink.skillviva.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body of POST /me/skills/{skillId}/viva/submit — the answered MCQs. {@code vivaId} points at the
 * server's stored copy of the question set from /start (nullable: without it the backend falls back
 * to regenerating the set, which is only exact for the deterministic rule-based adapter). Each entry
 * echoes the question it answers plus the exact text of the option the student picked; the backend
 * scores the pick against its own copy of the question, so the shuffled order the client saw never
 * has to line up with the server's.
 */
public record SubmitVivaRequest(
    String vivaId,
    @NotEmpty List<Answer> answers
) {

    public record Answer(
        String question,
        String option
    ) {
    }
}