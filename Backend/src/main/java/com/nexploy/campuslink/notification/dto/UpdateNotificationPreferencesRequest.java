package com.nexploy.campuslink.notification.dto;

import com.nexploy.campuslink.notification.entity.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Full replace of the caller's notification preferences (house style: replace over granular PATCH,
 * mirroring student goals). Only the types listed are upserted; unlisted types keep their default.
 */
public record UpdateNotificationPreferencesRequest(
    @NotEmpty @Valid List<PreferenceUpdate> preferences
) {
    public record PreferenceUpdate(@NotNull NotificationType type, boolean enabled) {
    }
}
