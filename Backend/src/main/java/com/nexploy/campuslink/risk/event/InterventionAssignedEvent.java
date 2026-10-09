package com.nexploy.campuslink.risk.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an intervention plan is raised for a student (DESIGN.md section 7:
 * {@code InterventionAssigned}).
 */
public record InterventionAssignedEvent(
    String eventId,
    String campusId,
    String planId,
    String studentId,
    String mentorUsername,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static InterventionAssignedEvent of(String campusId, String planId, String studentId,
                                               String mentorUsername, OffsetDateTime occurredAt) {
        return new InterventionAssignedEvent("evt_" + UUID.randomUUID(), campusId, planId, studentId,
            mentorUsername, occurredAt);
    }

    @Override
    public String eventType() {
        return "InterventionAssigned";
    }

    @Override
    public String aggregateId() {
        return planId;
    }

    @Override
    public String summary() {
        return "Intervention " + planId + " assigned to mentor " + mentorUsername
            + " for student " + studentId;
    }
}
