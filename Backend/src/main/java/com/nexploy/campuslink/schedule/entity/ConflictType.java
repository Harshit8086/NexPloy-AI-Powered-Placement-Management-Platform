package com.nexploy.campuslink.schedule.entity;

public enum ConflictType {
    VENUE_OVERLAP,
    PANEL_OVERLAP,
    PANEL_MEMBER_OVERLAP,
    VENUE_CAPACITY,
    CAMPUS_BLOCK,
    /** The same student is booked into two time-overlapping events (student double-booking). */
    STUDENT_OVERLAP
}
