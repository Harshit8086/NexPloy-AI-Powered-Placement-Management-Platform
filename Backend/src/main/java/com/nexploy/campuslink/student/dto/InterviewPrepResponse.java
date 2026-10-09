package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * A deterministic interview-preparation plan for one drive the signed-in student is in the pipeline
 * for. {@code preparationScore} and each technical topic's {@code coverage} come from the student's
 * own recorded skill proficiencies against the drive's stored required skills — nothing is
 * fabricated. When the student has no active applications the plan list is simply empty.
 */
public record InterviewPrepResponse(
    String driveId,
    String companyName,
    String role,
    int preparationScore,
    List<TechnicalTopic> technicalTopics,
    List<String> behaviouralTopics,
    List<String> likelyQuestions
) {

    /** coverage = the student's recorded proficiency in this required skill (0-100). */
    public record TechnicalTopic(String topic, int coverage) {}
}
