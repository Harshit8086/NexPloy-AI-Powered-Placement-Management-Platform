package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.ai.AnswerEvaluation;
import java.util.List;

/**
 * The per-answer scoring detail exposed to the client: the nine 0-100 metrics plus a short feedback
 * note and improvement bullets. Mirrors {@link AnswerEvaluation} (the AI-layer shape) but is a
 * stable API type.
 */
public record AnswerFeedback(
    int score,
    int correctness,
    int clarity,
    int relevance,
    int detail,
    int efficiency,
    int communication,
    int problemSolving,
    int creativity,
    String feedback,
    List<String> improvements
) {

    public static AnswerFeedback from(AnswerEvaluation e) {
        return new AnswerFeedback(
            e.score(), e.correctness(), e.clarity(), e.relevance(), e.detail(), e.efficiency(),
            e.communication(), e.problemSolving(), e.creativity(), e.feedback(),
            e.improvements() == null ? List.of() : e.improvements());
    }
}
