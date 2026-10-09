package com.nexploy.campuslink.schedule.exception;

/**
 * Raised when an optimization is requested for a conflict id that does not exist. Maps to HTTP 404.
 */
public class ScheduleConflictNotFoundException extends RuntimeException {

    public ScheduleConflictNotFoundException(String conflictId) {
        super("Schedule conflict not found: " + conflictId);
    }
}
