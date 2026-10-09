package com.nexploy.campuslink.document.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an officer/admin reviews a student's document (approves or rejects it). The
 * owning student is the interested party, so the notification consumer notifies them — the
 * {@code approved} flag selects the message and {@link
 * com.nexploy.campuslink.notification.entity.NotificationType}.
 */
public record DocumentReviewedEvent(
    String eventId,
    String campusId,
    String documentId,
    String studentId,
    boolean approved,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static DocumentReviewedEvent of(String campusId, String documentId, String studentId,
                                           boolean approved, OffsetDateTime occurredAt) {
        return new DocumentReviewedEvent("evt_" + UUID.randomUUID(), campusId, documentId, studentId,
            approved, occurredAt);
    }

    @Override
    public String eventType() {
        return "DocumentReviewed";
    }

    @Override
    public String aggregateId() {
        return documentId;
    }

    @Override
    public String summary() {
        return "Document " + documentId + (approved ? " approved" : " rejected") + " for student " + studentId;
    }
}
