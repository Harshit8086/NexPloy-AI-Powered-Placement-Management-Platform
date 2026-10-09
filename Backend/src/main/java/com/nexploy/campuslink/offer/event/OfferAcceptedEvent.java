package com.nexploy.campuslink.offer.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a student's offer is accepted (DESIGN.md section 7: {@code OfferAccepted}).
 */
public record OfferAcceptedEvent(
    String eventId,
    String campusId,
    String offerId,
    String studentId,
    String recruiterId,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static OfferAcceptedEvent of(String campusId, String offerId, String studentId,
                                        String recruiterId, OffsetDateTime occurredAt) {
        return new OfferAcceptedEvent("evt_" + UUID.randomUUID(), campusId, offerId, studentId,
            recruiterId, occurredAt);
    }

    @Override
    public String eventType() {
        return "OfferAccepted";
    }

    @Override
    public String aggregateId() {
        return offerId;
    }

    @Override
    public String summary() {
        return "Offer " + offerId + " accepted by student " + studentId;
    }
}
