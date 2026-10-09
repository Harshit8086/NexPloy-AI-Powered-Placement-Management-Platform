package com.nexploy.campuslink.shared.kafka;

import com.nexploy.campuslink.application.event.ApplicationAdvancedEvent;
import com.nexploy.campuslink.document.event.DocumentReviewedEvent;
import com.nexploy.campuslink.interview.event.InterviewCancelledEvent;
import com.nexploy.campuslink.interview.event.InterviewRescheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent;
import com.nexploy.campuslink.interview.event.InterviewScheduledEvent;
import com.nexploy.campuslink.matching.event.ShortlistDecidedEvent;
import com.nexploy.campuslink.offer.event.OfferAcceptedEvent;
import com.nexploy.campuslink.offer.event.OfferDeclinedEvent;
import com.nexploy.campuslink.offer.event.OfferExtendedEvent;
import com.nexploy.campuslink.offer.event.OfferRevokedEvent;
import com.nexploy.campuslink.risk.event.InterventionAssignedEvent;
import com.nexploy.campuslink.risk.event.StudentRiskDetectedEvent;
import com.nexploy.campuslink.shared.event.DomainEvent;
import java.util.Map;

/**
 * Maps a {@link DomainEvent#eventType()} string to the concrete event class so the Kafka consumer
 * can deserialize an envelope's payload back into the right record. Kept as an explicit table (not
 * reflection/classpath-scanning) so the set of Kafka-transported events is visible and reviewable
 * in one place; an unknown type resolves to {@code null} and the consumer skips it.
 */
public final class DomainEventTypeRegistry {

    private static final Map<String, Class<? extends DomainEvent>> BY_TYPE = Map.ofEntries(
        Map.entry("OfferExtended", OfferExtendedEvent.class),
        Map.entry("OfferAccepted", OfferAcceptedEvent.class),
        Map.entry("OfferDeclined", OfferDeclinedEvent.class),
        Map.entry("OfferRevoked", OfferRevokedEvent.class),
        Map.entry("ShortlistDecided", ShortlistDecidedEvent.class),
        Map.entry("ApplicationAdvanced", ApplicationAdvancedEvent.class),
        Map.entry("InterviewScheduled", InterviewScheduledEvent.class),
        Map.entry("InterviewRescheduled", InterviewRescheduledEvent.class),
        Map.entry("InterviewCancelled", InterviewCancelledEvent.class),
        Map.entry("InterviewResultRecorded", InterviewResultRecordedEvent.class),
        Map.entry("DocumentReviewed", DocumentReviewedEvent.class),
        Map.entry("InterventionAssigned", InterventionAssignedEvent.class),
        Map.entry("StudentRiskDetected", StudentRiskDetectedEvent.class));

    private DomainEventTypeRegistry() {
    }

    /** The concrete event class for {@code eventType}, or {@code null} if the type is not mapped. */
    public static Class<? extends DomainEvent> resolve(String eventType) {
        return BY_TYPE.get(eventType);
    }
}
