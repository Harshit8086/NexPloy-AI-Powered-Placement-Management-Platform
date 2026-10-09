package com.nexploy.campuslink.matching.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an officer records a shortlist decision (SHORTLIST or REJECT) for a student on a
 * drive. The student is the interested party, so the notification consumer notifies them — the
 * {@code rejected} flag lets it pick the right message and {@link
 * com.nexploy.campuslink.notification.entity.NotificationType}.
 */
public record ShortlistDecidedEvent(
    String eventId,
    String campusId,
    String driveId,
    String studentId,
    boolean rejected,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static ShortlistDecidedEvent of(String campusId, String driveId, String studentId,
                                           boolean rejected, OffsetDateTime occurredAt) {
        return new ShortlistDecidedEvent("evt_" + UUID.randomUUID(), campusId, driveId, studentId,
            rejected, occurredAt);
    }

    @Override
    public String eventType() {
        return "ShortlistDecided";
    }

    @Override
    public String aggregateId() {
        return driveId;
    }

    @Override
    public String summary() {
        return "Student " + studentId + (rejected ? " rejected" : " shortlisted") + " for drive " + driveId;
    }
}
