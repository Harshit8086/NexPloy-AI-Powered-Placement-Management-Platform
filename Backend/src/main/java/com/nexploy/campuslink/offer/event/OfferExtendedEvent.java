package com.nexploy.campuslink.offer.event;

import com.nexploy.campuslink.shared.event.DomainEvent;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Published when an officer extends a new offer to a student (DESIGN.md section 7:
 * {@code OfferExtended}). This is the canonical placement signal the student reacts to — the
 * notification consumer turns it into an in-app notification so the student learns of the offer
 * without any manual notification step.
 */
public record OfferExtendedEvent(
    String eventId,
    String campusId,
    String offerId,
    String studentId,
    String recruiterId,
    String role,
    OffsetDateTime occurredAt
) implements DomainEvent {

    public static OfferExtendedEvent of(String campusId, String offerId, String studentId,
                                        String recruiterId, String role, OffsetDateTime occurredAt) {
        return new OfferExtendedEvent("evt_" + UUID.randomUUID(), campusId, offerId, studentId,
            recruiterId, role, occurredAt);
    }

    @Override
    public String eventType() {
        return "OfferExtended";
    }

    @Override
    public String aggregateId() {
        return offerId;
    }

    @Override
    public String summary() {
        return "Offer " + offerId + " extended to student " + studentId;
    }
}
