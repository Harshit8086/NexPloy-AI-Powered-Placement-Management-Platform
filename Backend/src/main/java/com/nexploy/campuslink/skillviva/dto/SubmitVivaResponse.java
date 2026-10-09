package com.nexploy.campuslink.skillviva.dto;

import java.util.List;

/**
 * Result of a submitted skill viva. The viva recalibrates the skill's level: {@code achievedLevel}
 * is the measured proficiency (0-100) that the stored skill is now set to and VERIFIED at,
 * {@code claimedLevel} is what it was before, and {@code feedback} carries the per-question scores
 * plus the correct option text.
 */
public record SubmitVivaResponse(
    int achievedLevel,
    int claimedLevel,
    String verificationStatus,
    List<AnswerFeedback> feedback
) {

    public record AnswerFeedback(String question, int score, String correctOption) {
    }
}