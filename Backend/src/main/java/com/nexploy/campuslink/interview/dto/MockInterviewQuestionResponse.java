package com.nexploy.campuslink.interview.dto;

/**
 * One question in a {@link MockInterviewResponse}. {@code feedback} and {@code score} are null until
 * the question has been answered and evaluated.
 */
public record MockInterviewQuestionResponse(
    String id,
    int ordinal,
    String prompt,
    String difficulty,
    Integer timerSeconds,
    boolean coding,
    String language,
    String studentAnswer,
    Integer score,
    AnswerFeedback feedback
) {
}
