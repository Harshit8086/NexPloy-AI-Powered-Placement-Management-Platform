package com.nexploy.campuslink.notification.channel;

import com.nexploy.campuslink.notification.entity.NotificationChannel;

/**
 * An outbound delivery channel for notifications (email, SMS). Implementations are registered as
 * Spring beans only when their channel is enabled (see {@code @ConditionalOnProperty} on each), so
 * the {@link com.nexploy.campuslink.notification.service.NotificationDispatchService dispatcher}
 * only ever sees the channels that are actually configured. The in-app channel has no sender —
 * an in-app notification IS its own delivery (persisted as SENT immediately).
 */
public interface NotificationSender {

    /** The channel this sender delivers on. */
    NotificationChannel channel();

    /**
     * Delivers one notification. {@code recipientUsername} is the recipient's account username,
     * which in this system is their email address; SMS senders resolve a phone number from it.
     * Throws on any delivery failure so the dispatcher can record a failed attempt and retry.
     */
    void send(String recipientUsername, String subject, String body);
}
