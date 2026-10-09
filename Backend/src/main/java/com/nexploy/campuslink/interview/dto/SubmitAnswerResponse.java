package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.MockInterviewStatus;

/**
 * Result of submitting one answer: the scoring detail for that answer plus the interview's new
 * position, so the client knows whether to advance to the next question or move to completion.
 */
public record SubmitAnswerResponse(
    int ordinal,
    AnswerFeedback feedback,
    MockInterviewStatus status,
    int currentQuestion,
    int totalQuestions,
    boolean completed
) {
}
