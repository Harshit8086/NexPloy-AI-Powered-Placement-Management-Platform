package com.nexploy.campuslink.shared.event;

import java.time.OffsetDateTime;

/** Frontend-facing view of a recorded domain event (camelCase). */
public record DomainEventResponse(
    String id,
    String eventType,
    String campusId,
    String aggregateId,
    String summary,
    OffsetDateTime occurredAt,
    OffsetDateTime recordedAt
) {

    public static DomainEventResponse from(DomainEventRecord record) {
        return new DomainEventResponse(
            record.getId(), record.getEventType(), record.getCampusId(), record.getAggregateId(),
            record.getSummary(), record.getOccurredAt(), record.getRecordedAt());
    }
}
