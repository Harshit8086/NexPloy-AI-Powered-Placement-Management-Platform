package com.nexploy.campuslink.shared.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges in-process {@link DomainEvent}s onto the Kafka topic when the Kafka transport is enabled
 * ({@code nexploy.kafka.enabled=true}). Like the in-JVM listeners it fires only
 * {@link TransactionPhase#AFTER_COMMIT}, so nothing is published for work that rolled back, and it
 * keys each record by the aggregate id so all events for one aggregate keep their order on a
 * partition. The event is wrapped in a {@link DomainEventEnvelope} (type + JSON payload) and sent as
 * a JSON string; {@link KafkaNotificationConsumer} reads it back.
 *
 * <p>When this bridge is active the in-process {@code NotificationEventListener} stands down (it is
 * gated on {@code nexploy.kafka.enabled=false}), so a domain event drives a notification exactly
 * once — via Kafka. The audit listener stays in-process regardless (it only records the trail).
 */
@Component
@ConditionalOnProperty(name = "nexploy.kafka.enabled", havingValue = "true")
public class DomainEventKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(DomainEventKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${nexploy.kafka.topic}")
    private String topic;

    public DomainEventKafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes every committed domain event to Kafka. Runs in its own new transaction so a failure
     * here never rolls back the originating business operation; a send failure is logged and dropped
     * (the audit trail still holds the event, and the topic is a best-effort transport, not the
     * system of record).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDomainEvent(DomainEvent event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event);
            String envelopeJson = objectMapper.writeValueAsString(
                new DomainEventEnvelope(event.eventType(), payloadJson));
            kafkaTemplate.send(topic, event.aggregateId(), envelopeJson);
        } catch (Exception e) {
            log.warn("Failed to publish domain event {} ({}) to Kafka topic {}: {}",
                event.eventId(), event.eventType(), topic, e.getMessage());
        }
    }
}
