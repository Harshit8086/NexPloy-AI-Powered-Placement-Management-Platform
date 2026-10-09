package com.nexploy.campuslink.notification.repository;

import com.nexploy.campuslink.notification.entity.NotificationPreference;
import com.nexploy.campuslink.notification.entity.NotificationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    /** All of a user's explicit preference rows (missing types default to enabled). */
    List<NotificationPreference> findByUsername(String username);

    /** A single preference override, used both for the enforcement gate and upserts. */
    Optional<NotificationPreference> findByUsernameAndType(String username, NotificationType type);
}
