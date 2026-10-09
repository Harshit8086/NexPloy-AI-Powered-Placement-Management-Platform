package com.nexploy.campuslink.notification.dto;

import com.nexploy.campuslink.notification.entity.DeliveryAttempt;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import java.time.OffsetDateTime;

/**
 * One row of a notification's delivery-attempt trail, surfaced in the admin console so an
 * officer/admin can see exactly when each send was tried and why it failed.
 */
public record DeliveryAttemptResponse(
    String id,
    String notificationId,
    int attemptNumber,
    NotificationStatus outcome,
    String detail,
    OffsetDateTime attemptedAt
) {

    public static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(
            attempt.getId(),
            attempt.getNotificationId(),
            attempt.getAttemptNumber(),
            attempt.getOutcome(),
            attempt.getDetail(),
            attempt.getAttemptedAt()
        );
    }
}
