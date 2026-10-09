package com.nexploy.campuslink.notification.template;

/**
 * A fully rendered notification message — the subject and body produced by a
 * {@link NotificationTemplate} after variable substitution.
 */
public record RenderedNotification(String subject, String body) {
}
