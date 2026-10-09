package com.nexploy.campuslink.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.nexploy.campuslink.shared.kafka.DomainEventEnvelope;
import com.nexploy.campuslink.shared.kafka.DomainEventTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka-on delivery of domain events to notifications. Consumes the {@code nexploy.kafka.topic}
 * topic, deserializes each {@link DomainEventEnvelope} back into its concrete
 * {@link DomainEvent} (via {@link DomainEventTypeRegistry}), and dispatches to the shared
 * {@link NotificationEventHandlers} — the same logic the in-process listener uses when Kafka is off.
 *
 * <p>Active only when {@code nexploy.kafka.enabled=true}; the in-process
 * {@link NotificationEventListener} stands down in that mode so each event yields one notification.
 * Notification creation is idempotent per (source event, recipient), so a Kafka redelivery — the
 * at-least-once default — is safe: a re-consumed event creates no duplicate notification.
 */
@Component
@ConditionalOnProperty(name = "nexploy.kafka.enabled", havingValue = "true")
public class KafkaNotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaNotificationConsumer.class);

    private final NotificationEventHandlers handlers;
    private final ObjectMapper objectMapper;

    public KafkaNotificationConsumer(NotificationEventHandlers handlers, ObjectMapper objectMapper) {
        this.handlers = handlers;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${nexploy.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String envelopeJson) {
        DomainEvent event;
        try {
            DomainEventEnvelope envelope = objectMapper.readValue(envelopeJson, DomainEventEnvelope.class);
            Class<? extends DomainEvent> type = DomainEventTypeRegistry.resolve(envelope.eventType());
            if (type == null) {
                log.debug("Ignoring domain event of unmapped type {}", envelope.eventType());
                return;
            }
            event = objectMapper.readValue(envelope.payloadJson(), type);
        } catch (Exception e) {
            // A malformed message must not stall the partition; log and move on.
            log.warn("Skipping unreadable Kafka domain-event message: {}", e.getMessage());
            return;
        }
        dispatch(event);
    }

    /** Routes a deserialized event to the matching handler. Unknown concrete types are ignored. */
    private void dispatch(DomainEvent event) {
        if (event instanceof OfferExtendedEvent e) {
            handlers.onOfferExtended(e);
        } else if (event instanceof OfferAcceptedEvent e) {
            handlers.onOfferAccepted(e);
        } else if (event instanceof OfferDeclinedEvent e) {
            handlers.onOfferDeclined(e);
        } else if (event instanceof OfferRevokedEvent e) {
            handlers.onOfferRevoked(e);
        } else if (event instanceof ShortlistDecidedEvent e) {
            handlers.onShortlistDecided(e);
        } else if (event instanceof ApplicationAdvancedEvent e) {
            handlers.onApplicationAdvanced(e);
        } else if (event instanceof InterviewScheduledEvent e) {
            handlers.onInterviewScheduled(e);
        } else if (event instanceof InterviewRescheduledEvent e) {
            handlers.onInterviewRescheduled(e);
        } else if (event instanceof InterviewCancelledEvent e) {
            handlers.onInterviewCancelled(e);
        } else if (event instanceof InterviewResultRecordedEvent e) {
            handlers.onInterviewResultRecorded(e);
        } else if (event instanceof DocumentReviewedEvent e) {
            handlers.onDocumentReviewed(e);
        } else if (event instanceof InterventionAssignedEvent e) {
            handlers.onInterventionAssigned(e);
        } else if (event instanceof StudentRiskDetectedEvent e) {
            handlers.onStudentRiskDetected(e);
        } else {
            log.debug("No notification handler for event type {}", event.eventType());
        }
    }
}
