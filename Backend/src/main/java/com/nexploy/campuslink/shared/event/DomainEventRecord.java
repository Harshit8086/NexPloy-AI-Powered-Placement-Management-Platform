package com.nexploy.campuslink.shared.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The persisted audit trail of domain events (DESIGN.md section 7: "Consumers update notifications,
 * analytics, and audit records asynchronously. Use idempotency keys..."). The event id is the
 * primary key, so recording the same event twice is a no-op — this is the idempotency guarantee a
 * redelivered or replayed event needs.
 */
@Entity
@Table(name = "domain_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DomainEventRecord {

    /** The event's idempotency key. */
    @Id
    private String id;

    private String eventType;
    private String campusId;
    private String aggregateId;

    @Column(length = 1000)
    private String summary;

    private OffsetDateTime occurredAt;
    private OffsetDateTime recordedAt;
}
