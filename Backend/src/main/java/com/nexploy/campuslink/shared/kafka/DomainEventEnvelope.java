package com.nexploy.campuslink.shared.kafka;

/**
 * Wire format for a {@link com.nexploy.campuslink.shared.event.DomainEvent} on the Kafka topic. The
 * concrete event records are polymorphic (many types implement the interface), so rather than rely
 * on Jackson type headers we carry the event type name explicitly alongside the event serialized as
 * a JSON string. The consumer reads {@code eventType}, resolves the concrete class via
 * {@link DomainEventTypeRegistry}, and deserializes {@code payloadJson} into it.
 *
 * @param eventType   the {@link com.nexploy.campuslink.shared.event.DomainEvent#eventType()} value
 * @param payloadJson the concrete event record serialized as JSON
 */
public record DomainEventEnvelope(String eventType, String payloadJson) {
}
