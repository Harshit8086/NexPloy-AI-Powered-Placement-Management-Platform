package com.nexploy.campuslink.notification.dto;

import com.nexploy.campuslink.notification.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
    @NotBlank String recipientUsername,
    @NotNull NotificationChannel channel,
    @NotBlank @Size(max = 200) String subject,
    @NotBlank @Size(max = 4000) String body
) {
}
