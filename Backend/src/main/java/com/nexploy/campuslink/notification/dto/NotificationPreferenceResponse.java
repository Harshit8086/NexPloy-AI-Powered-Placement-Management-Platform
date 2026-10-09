package com.nexploy.campuslink.notification.dto;

import com.nexploy.campuslink.notification.entity.NotificationType;

/** One resolved preference row: whether a given notification type is delivered to the user. */
public record NotificationPreferenceResponse(NotificationType type, boolean enabled) {
}
