package com.nexploy.campuslink.offer.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when a student declines an extended offer. The recruiter who made the offer is the
 * interested party (their candidate said no), so the notification consumer notifies them.
 */
public record OfferDeclinedEvent(
    String eventId,
    String campusId,
    String offerId,
    String studentId,
    String recruiterId,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static OfferDeclinedEvent of(String campusId, String offerId, String studentId,
                                        String recruiterId, OffsetDateTime occurredAt) {
        return new OfferDeclinedEvent("evt_" + UUID.randomUUID(), campusId, offerId, studentId,
            recruiterId, occurredAt);
    }

    @Override
    public String eventType() {
        return "OfferDeclined";
    }

    @Override
    public String aggregateId() {
        return offerId;
    }

    @Override
    public String summary() {
        return "Offer " + offerId + " declined by student " + studentId;
    }
}
