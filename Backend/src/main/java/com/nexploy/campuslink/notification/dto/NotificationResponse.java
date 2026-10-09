package com.nexploy.campuslink.notification.dto;

import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.entity.NotificationType;
import java.time.OffsetDateTime;

public record NotificationResponse(
    String id,
    String campusId,
    String recipientUsername,
    NotificationChannel channel,
    NotificationType type,
    String referenceId,
    String subject,
    String body,
    NotificationStatus status,
    int attemptCount,
    OffsetDateTime createdAt,
    OffsetDateTime lastAttemptAt,
    OffsetDateTime readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getCampusId(),
            notification.getRecipientUsername(),
            notification.getChannel(),
            notification.getType(),
            notification.getReferenceId(),
            notification.getSubject(),
            notification.getBody(),
            notification.getStatus(),
            notification.getAttemptCount(),
            notification.getCreatedAt(),
            notification.getLastAttemptAt(),
            notification.getReadAt()
        );
    }
}
