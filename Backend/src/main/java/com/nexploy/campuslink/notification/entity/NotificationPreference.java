package com.nexploy.campuslink.notification.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A recipient's opt-in/out choice for one {@link NotificationType}. Opt-out model: the absence of a
 * row means the type is enabled (the default), so this table stores only explicit choices. When a
 * type is disabled, the event consumer skips creating that notification for this user.
 *
 * <p>Preferences are keyed by {@code (username, type)} and self-scoped — a user only ever reads or
 * writes their own. Channel granularity is intentionally deferred: only IN_APP notifications are
 * created today, so a per-type toggle is the meaningful control (see the notification build notes).
 */
@Entity
@Table(name = "notification_preferences",
    uniqueConstraints = @UniqueConstraint(name = "uq_notification_pref_user_type",
        columnNames = {"username", "type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    private String id;

    private String username;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    /** Whether the user wants this notification type delivered. False = muted. */
    private boolean enabled;

    private OffsetDateTime updatedAt;
}
