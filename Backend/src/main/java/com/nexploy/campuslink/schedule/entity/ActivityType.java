package com.nexploy.campuslink.schedule.entity;

/**
 * The kind of drive activity a {@link ScheduleEvent} represents. Added additively (V50): existing
 * events default to {@link #INTERVIEW}, preserving the original 1-to-1 interview scheduling. Batch
 * activities (aptitude/coding tests, group discussions, orientations) sit alongside interviews on
 * the same {@code schedule_events} table — one row per activity, sized by {@code participantCount} —
 * so venue-capacity and time-overlap conflict detection applies to them unchanged.
 */
public enum ActivityType {
    INTERVIEW,
    APTITUDE_TEST,
    CODING_TEST,
    GROUP_DISCUSSION,
    PRESENTATION,
    ASSESSMENT,
    ORIENTATION,
    OTHER
}
