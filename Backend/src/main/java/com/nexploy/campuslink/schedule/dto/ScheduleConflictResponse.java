package com.nexploy.campuslink.schedule.dto;

import com.nexploy.campuslink.schedule.entity.ConflictType;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;

import java.time.OffsetDateTime;
import java.util.List;

public record ScheduleConflictResponse(
    ConflictType type,
    String eventId,
    String conflictingEventId,
    String resourceId,
    OffsetDateTime overlapStart,
    OffsetDateTime overlapEnd,
    String message
) {

    public static ScheduleConflictResponse overlap(
        ConflictType type,
        ScheduleEvent event,
        ScheduleEvent conflictingEvent,
        String resourceId,
        OffsetDateTime overlapStart,
        OffsetDateTime overlapEnd
    ) {
        return new ScheduleConflictResponse(
            type,
            event.getId(),
            conflictingEvent.getId(),
            resourceId,
            overlapStart,
            overlapEnd,
            type + " between " + event.getId() + " and " + conflictingEvent.getId()
        );
    }

    public static ScheduleConflictResponse capacity(ScheduleEvent event, int capacity) {
        return new ScheduleConflictResponse(
            ConflictType.VENUE_CAPACITY,
            event.getId(),
            null,
            event.getVenueId(),
            event.getStartsAt(),
            event.getEndsAt(),
            "Expected " + event.getExpectedParticipants() + " participants exceed venue capacity " + capacity
        );
    }

    public static ScheduleConflictResponse blockedSlot(
        ScheduleEvent event,
        String reason,
        OffsetDateTime overlapStart,
        OffsetDateTime overlapEnd
    ) {
        return new ScheduleConflictResponse(
            ConflictType.CAMPUS_BLOCK,
            event.getId(),
            null,
            event.getVenueId(),
            overlapStart,
            overlapEnd,
            "Event overlaps blocked slot: " + reason
        );
    }
}

