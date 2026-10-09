package com.nexploy.campuslink.shared.event;

import java.time.OffsetDateTime;

/**
 * Base contract for domain events (DESIGN.md section 7). A domain event is a past-tense fact —
 * something that already happened inside a committed transaction — that consumers (notifications,
 * analytics, audit) react to. Every event carries a stable {@link #eventId()} used as an
 * idempotency key so a consumer can process it exactly once, a type discriminator, the campus it
 * belongs to (so consumers stay campus-aware), the id of the aggregate it concerns, and when it
 * occurred.
 */
public interface DomainEvent {

    String eventId();

    String eventType();

    String campusId();

    String aggregateId();

    OffsetDateTime occurredAt();

    /**
     * A short, human-readable summary of the fact, stored on the audit record. Consumers that need
     * structured data should read the concrete event type; this is for the audit log and logging.
     */
    String summary();
}
