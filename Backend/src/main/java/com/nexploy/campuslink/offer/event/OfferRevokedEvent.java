package com.nexploy.campuslink.offer.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an officer/admin revokes an extended offer. The student whose offer was withdrawn
 * is the interested party, so the notification consumer notifies them.
 */
public record OfferRevokedEvent(
    String eventId,
    String campusId,
    String offerId,
    String studentId,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static OfferRevokedEvent of(String campusId, String offerId, String studentId,
                                       OffsetDateTime occurredAt) {
        return new OfferRevokedEvent("evt_" + UUID.randomUUID(), campusId, offerId, studentId,
            occurredAt);
    }

    @Override
    public String eventType() {
        return "OfferRevoked";
    }

    @Override
    public String aggregateId() {
        return offerId;
    }

    @Override
    public String summary() {
        return "Offer " + offerId + " revoked for student " + studentId;
    }
}
