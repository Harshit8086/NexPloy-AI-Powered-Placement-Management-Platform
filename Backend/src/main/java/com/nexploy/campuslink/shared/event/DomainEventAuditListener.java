package com.nexploy.campuslink.shared.event;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records every published {@link DomainEvent} to the audit trail (DESIGN.md section 7). It fires
 * only after the publishing transaction commits ({@link TransactionPhase#AFTER_COMMIT}), so an
 * event is never recorded for work that rolled back, and runs in its own new transaction. Recording
 * is idempotent: the event id is the primary key, so a replayed or redelivered event is written at
 * most once.
 *
 * <p>This first pass records synchronously after commit; making it asynchronous (a thread pool or a
 * durable outbox) is a later step and does not change the event contract or the idempotency key.
 */
@Component
@RequiredArgsConstructor
public class DomainEventAuditListener {

    private final DomainEventRecordRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(DomainEvent event) {
        if (repository.existsById(event.eventId())) {
            return;
        }
        repository.save(DomainEventRecord.builder()
            .id(event.eventId())
            .eventType(event.eventType())
            .campusId(event.campusId())
            .aggregateId(event.aggregateId())
            .summary(event.summary())
            .occurredAt(event.occurredAt())
            .recordedAt(OffsetDateTime.now())
            .build());
    }
}
