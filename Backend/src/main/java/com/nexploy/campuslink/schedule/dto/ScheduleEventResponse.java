package com.nexploy.campuslink.schedule.dto;

import com.nexploy.campuslink.schedule.entity.ActivityType;
import com.nexploy.campuslink.schedule.entity.ParticipantMode;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import java.time.OffsetDateTime;

/**
 * A scheduled event enriched with the human-readable venue and panel names resolved from the
 * schedule module's own {@code venues}/{@code panels} tables, so the calendar can render without a
 * second round-trip. Names are {@code null} when the referenced resource no longer exists — never
 * fabricated. Company/drive labelling is resolved client-side from the (already live) drives list,
 * so this DTO deliberately does not reach across into the placement module.
 */
public record ScheduleEventResponse(
    String id,
    String driveId,
    String campusId,
    String venueId,
    String venueName,
    String panelId,
    String panelName,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer expectedParticipants,
    Integer venueCapacity,
    boolean conflict,
    ActivityType activityType,
    ParticipantMode participantMode,
    Integer participantCount
) {

    public static ScheduleEventResponse from(ScheduleEvent event, String venueName, String panelName) {
        return new ScheduleEventResponse(
            event.getId(),
            event.getDriveId(),
            event.getCampusId(),
            event.getVenueId(),
            venueName,
            event.getPanelId(),
            panelName,
            event.getStartsAt(),
            event.getEndsAt(),
            event.getExpectedParticipants(),
            event.getVenueCapacity(),
            event.isConflict(),
            event.getActivityType(),
            event.getParticipantMode(),
            event.getParticipantCount()
        );
    }
}
