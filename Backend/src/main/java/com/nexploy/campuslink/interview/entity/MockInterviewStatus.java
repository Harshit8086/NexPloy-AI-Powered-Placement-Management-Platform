package com.nexploy.campuslink.interview.entity;

/**
 * Lifecycle of an AI practice interview. Kept separate from the placement {@link InterviewStatus}
 * so the two domains never share state: a mock interview is either still being answered or finished
 * and scored.
 */
public enum MockInterviewStatus {
    IN_PROGRESS,
    COMPLETED
}
