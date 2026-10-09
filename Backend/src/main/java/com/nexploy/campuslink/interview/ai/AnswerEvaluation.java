package com.nexploy.campuslink.interview.ai;

import java.util.List;

/**
 * The evaluation of a single answer across the nine practice-interview metrics (each 0-100), plus a
 * short prose {@code feedback} note and concrete {@code improvements}. {@code score} is the headline
 * for the answer. This is read-only evidence: it never mutates authorization, eligibility or scores
 * of record.
 */
public record AnswerEvaluation(
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
}
