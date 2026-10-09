package com.nexploy.campuslink.student.dto;

/** The five assessment scores (0-100). Maps 1:1 to the frontend AssessmentScores. */
public record AssessmentScores(
    Integer aptitude,
    Integer coding,
    Integer technical,
    Integer communication,
    Integer mockInterview
) {
}
