package com.nexploy.campuslink.aptitude.entity;

/**
 * Where an aptitude question came from. Bank rows (SEEDED/OFFICER, student_id NULL) are eligible
 * for official test draws; AI_GENERATED rows are a student's private practice items and are
 * excluded from official draws by the repository filter.
 */
public enum AptitudeQuestionSource {
    SEEDED,
    OFFICER,
    AI_GENERATED
}