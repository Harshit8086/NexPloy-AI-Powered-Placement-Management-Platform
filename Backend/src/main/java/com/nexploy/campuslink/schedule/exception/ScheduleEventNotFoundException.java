package com.nexploy.campuslink.schedule.exception;

public class ScheduleEventNotFoundException extends RuntimeException {

    public ScheduleEventNotFoundException(String eventId) {
        super("Schedule event not found: " + eventId);
    }
}
