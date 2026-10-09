package com.nexploy.campuslink.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    private String campusId;
    private String recipientUsername;

    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    /**
     * Semantic type of the notification (the business fact). Null for ad-hoc, manually-created
     * notifications; always set for event-driven ones. Drives client deep-linking and filtering.
     */
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /**
     * Id of the domain entity this notification is about (offer id, interview id, application id …),
     * paired with {@link #type} so the client can build a deep link to the relevant page. Nullable.
     */
    private String referenceId;

    private String subject;
    private String body;

    /**
     * The id of the {@link com.nexploy.campuslink.shared.event.DomainEvent} that produced this
     * notification, when it was created by the event consumer rather than an officer. Null for
     * manually-created notifications. Used as the idempotency key so a replayed event never
     * produces a duplicate notification.
     */
    private String sourceEventId;

    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    private int attemptCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastAttemptAt;
    private OffsetDateTime readAt;
}
