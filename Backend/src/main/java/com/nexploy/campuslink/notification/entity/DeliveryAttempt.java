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

/**
 * Immutable record of a single delivery attempt for a notification. Required by
 * SECURITY.md section 4: notification delivery attempts must be traceable.
 */
@Entity
@Table(name = "notification_delivery_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttempt {

    @Id
    private String id;

    private String notificationId;
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    private NotificationStatus outcome;

    private String detail;
    private OffsetDateTime attemptedAt;
}
