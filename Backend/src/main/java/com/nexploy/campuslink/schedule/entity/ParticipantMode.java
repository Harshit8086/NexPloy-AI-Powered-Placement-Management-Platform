package com.nexploy.campuslink.schedule.entity;

/**
 * Whether a {@link ScheduleEvent} books a single candidate ({@link #INDIVIDUAL}, e.g. a 1-to-1
 * interview) or a group in one sitting ({@link #BATCH}, e.g. an aptitude test for a cohort). Added
 * additively (V50); existing events default to {@link #INDIVIDUAL}. The
 * {@code PARTICIPANTS_PER_INTERVIEW = 1} rule stays scoped to INDIVIDUAL interview mirrors — BATCH
 * events instead carry their real {@code participantCount}, which capacity detection checks against
 * the venue.
 */
public enum ParticipantMode {
    INDIVIDUAL,
    BATCH
}
