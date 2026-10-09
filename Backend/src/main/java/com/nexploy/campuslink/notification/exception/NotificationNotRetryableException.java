package com.nexploy.campuslink.notification.exception;

/**
 * Thrown when an officer/admin asks the admin console to re-queue a notification that isn't a
 * failed outbound (email/SMS) row — e.g. an in-app row (no external delivery) or one that is still
 * PENDING/SENT. A business-rule conflict, mapped to HTTP 409.
 */
public class NotificationNotRetryableException extends RuntimeException {

    public NotificationNotRetryableException(String message) {
        super(message);
    }
}
