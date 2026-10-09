package com.nexploy.campuslink.interview.entity;

/**
 * Kind of AI practice interview a student runs. Distinct from the placement {@link InterviewStatus}
 * lifecycle — this classifies a self-service mock/practice session, not a scheduled campus round.
 */
public enum MockInterviewType {
    TECHNICAL,
    BEHAVIOURAL,
    CODING,
    MIXED
}
