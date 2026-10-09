package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.notification.dto.NotificationPreferenceResponse;
import com.nexploy.campuslink.notification.dto.UpdateNotificationPreferencesRequest;
import com.nexploy.campuslink.notification.entity.NotificationPreference;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.repository.NotificationPreferenceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages per-recipient notification preferences (see {@link NotificationPreference}). Opt-out model:
 * a type with no stored row is enabled. All reads/writes are self-scoped to the authenticated user;
 * the enforcement gate {@link #isEnabled} takes an explicit username because the caller is the system
 * event consumer acting on a recipient other than the signed-in user.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final CampusAccessService campusAccessService;

    /** The current user's resolved preference for every notification type (missing → enabled). */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> list() {
        UserAccount user = campusAccessService.currentUser();
        Map<NotificationType, Boolean> overrides = preferenceRepository.findByUsername(user.getUsername())
            .stream()
            .collect(Collectors.toMap(NotificationPreference::getType, NotificationPreference::isEnabled));
        return java.util.Arrays.stream(NotificationType.values())
            .map(type -> new NotificationPreferenceResponse(type, overrides.getOrDefault(type, true)))
            .toList();
    }

    /** Upserts the listed preferences for the current user, then returns the full resolved set. */
    @Transactional
    public List<NotificationPreferenceResponse> replace(UpdateNotificationPreferencesRequest request) {
        UserAccount user = campusAccessService.currentUser();
        OffsetDateTime now = OffsetDateTime.now();
        Map<NotificationType, NotificationPreference> existing = preferenceRepository
            .findByUsername(user.getUsername()).stream()
            .collect(Collectors.toMap(NotificationPreference::getType, Function.identity()));

        for (UpdateNotificationPreferencesRequest.PreferenceUpdate update : request.preferences()) {
            NotificationPreference pref = existing.get(update.type());
            if (pref == null) {
                pref = NotificationPreference.builder()
                    .id(UUID.randomUUID().toString())
                    .username(user.getUsername())
                    .type(update.type())
                    .build();
            }
            pref.setEnabled(update.enabled());
            pref.setUpdatedAt(now);
            preferenceRepository.save(pref);
        }
        return list();
    }

    /**
     * Whether the given type is enabled for the recipient. Called by the event consumer before
     * creating an event-driven notification. A null type (ad-hoc notifications) is always allowed.
     */
    @Transactional(readOnly = true)
    public boolean isEnabled(String username, NotificationType type) {
        if (type == null || username == null || username.isBlank()) {
            return true;
        }
        return preferenceRepository.findByUsernameAndType(username, type)
            .map(NotificationPreference::isEnabled)
            .orElse(true);
    }
}
