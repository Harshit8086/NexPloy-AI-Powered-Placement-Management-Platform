package com.nexploy.campuslink.aptitude.entity;

/**
 * OFFICIAL attempts are bank-drawn, deterministically scored and (when the assessment bridge is
 * enabled) feed the APTITUDE readiness dimension. PRACTICE attempts are AI-generated self-practice
 * and never publish the completion event, so they can never touch assessment_records.
 */
public enum AptitudeAttemptMode {
    OFFICIAL,
    PRACTICE
}