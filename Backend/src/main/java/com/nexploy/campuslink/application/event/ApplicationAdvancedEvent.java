package com.nexploy.campuslink.application.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an officer/recruiter manually advances an application through the placement
 * funnel (ASSESSMENT, INTERVIEW, SELECTED, OFFER, ...) via
 * {@code ApplicationService#changeStatus}. Shortlist/reject decisions use
 * {@code ShortlistDecidedEvent} instead; this event covers every other forward step so the
 * student is kept informed — the notification consumer resolves the student to their username
 * and creates the {@code APPLICATION_ADVANCED} notification.
 */
public record ApplicationAdvancedEvent(
    String eventId,
    String campusId,
    String driveId,
    String studentId,
    String newStatus,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static ApplicationAdvancedEvent of(String campusId, String driveId, String studentId,
                                              String newStatus, OffsetDateTime occurredAt) {
        return new ApplicationAdvancedEvent("evt_" + UUID.randomUUID(), campusId, driveId, studentId,
            newStatus, occurredAt);
    }

    @Override
    public String eventType() {
        return "ApplicationAdvanced";
    }

    @Override
    public String aggregateId() {
        return driveId;
    }

    @Override
    public String summary() {
        return "Application for student " + studentId + " advanced to " + newStatus + " for drive " + driveId;
    }
}