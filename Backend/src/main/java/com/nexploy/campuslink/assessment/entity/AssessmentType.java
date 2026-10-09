package com.nexploy.campuslink.assessment.entity;

/**
 * The five assessment dimensions CampusLink tracks. Each maps 1:1 to a field of the student's
 * current assessment snapshot ({@code AssessmentScores}: aptitude, coding, technical,
 * communication, mockInterview). Bulk-imported rows must carry one of these types; anything
 * else is rejected per-row as {@code INVALID_TYPE}.
 */
public enum AssessmentType {
    APTITUDE,
    CODING,
    TECHNICAL,
    COMMUNICATION,
    MOCK_INTERVIEW;

    /** Case-insensitive parse; returns {@code null} for a null/blank/unknown value (never throws). */
    public static AssessmentType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AssessmentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
