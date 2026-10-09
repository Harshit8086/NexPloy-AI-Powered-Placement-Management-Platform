package com.nexploy.campuslink.skillviva.dto;

import java.util.List;

/**
 * Response of POST /me/skills/{skillId}/viva/start — the skill under test and its generated
 * questions. {@code vivaId} identifies the server's short-lived copy of this exact question set;
 * the client echoes it back on submit so it is scored against the questions it actually saw (the
 * LLM adapter is non-deterministic and cannot simply regenerate the same set).
 */
public record StartVivaResponse(
    String vivaId,
    String skillId,
    String skill,
    int targetLevel,
    List<VivaQuestionResponse> questions
) {
}
