package com.nexploy.campuslink.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.notification.dto.NotificationPreferenceResponse;
import com.nexploy.campuslink.notification.dto.UpdateNotificationPreferencesRequest;
import com.nexploy.campuslink.notification.dto.UpdateNotificationPreferencesRequest.PreferenceUpdate;
import com.nexploy.campuslink.notification.entity.NotificationPreference;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.repository.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class NotificationPreferenceServiceTest {

    private final NotificationPreferenceRepository preferenceRepository =
        org.mockito.Mockito.mock(NotificationPreferenceRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final NotificationPreferenceService service =
        new NotificationPreferenceService(preferenceRepository, campusAccessService);

    @Test
    void listDefaultsEveryTypeToEnabledWhenNoOverrides() {
        authenticate(user("student-1"));
        when(preferenceRepository.findByUsername("student-1")).thenReturn(List.of());

        List<NotificationPreferenceResponse> prefs = service.list();

        assertThat(prefs).hasSize(NotificationType.values().length);
        assertThat(prefs).allMatch(NotificationPreferenceResponse::enabled);
    }

    @Test
    void listReflectsAStoredDisableOverride() {
        authenticate(user("student-1"));
        when(preferenceRepository.findByUsername("student-1"))
            .thenReturn(List.of(pref("student-1", NotificationType.OFFER_ACCEPTED, false)));

        List<NotificationPreferenceResponse> prefs = service.list();

        assertThat(prefs).filteredOn(p -> p.type() == NotificationType.OFFER_ACCEPTED)
            .allMatch(p -> !p.enabled());
        assertThat(prefs).filteredOn(p -> p.type() == NotificationType.OFFER_EXTENDED)
            .allMatch(NotificationPreferenceResponse::enabled);
    }

    @Test
    void replaceInsertsANewOverrideForTheCurrentUser() {
        authenticate(user("student-1"));
        when(preferenceRepository.findByUsername("student-1")).thenReturn(List.of());
        when(preferenceRepository.findByUsernameAndType("student-1", NotificationType.RISK_DETECTED))
            .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.replace(new UpdateNotificationPreferencesRequest(
            List.of(new PreferenceUpdate(NotificationType.RISK_DETECTED, false))));

        org.mockito.ArgumentCaptor<NotificationPreference> captor =
            org.mockito.ArgumentCaptor.forClass(NotificationPreference.class);
        org.mockito.Mockito.verify(preferenceRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("student-1");
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.RISK_DETECTED);
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getId()).isNotBlank();
    }

    @Test
    void replaceUpdatesAnExistingOverrideInPlace() {
        authenticate(user("student-1"));
        NotificationPreference existing = pref("student-1", NotificationType.OFFER_ACCEPTED, false);
        when(preferenceRepository.findByUsername("student-1")).thenReturn(List.of(existing));
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.replace(new UpdateNotificationPreferencesRequest(
            List.of(new PreferenceUpdate(NotificationType.OFFER_ACCEPTED, true))));

        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getUpdatedAt()).isNotNull();
    }

    @Test
    void isEnabledDefaultsToTrueWhenNoRow() {
        when(preferenceRepository.findByUsernameAndType("student-1", NotificationType.OFFER_ACCEPTED))
            .thenReturn(Optional.empty());

        assertThat(service.isEnabled("student-1", NotificationType.OFFER_ACCEPTED)).isTrue();
    }

    @Test
    void isEnabledIsFalseWhenTypeMuted() {
        when(preferenceRepository.findByUsernameAndType("student-1", NotificationType.OFFER_ACCEPTED))
            .thenReturn(Optional.of(pref("student-1", NotificationType.OFFER_ACCEPTED, false)));

        assertThat(service.isEnabled("student-1", NotificationType.OFFER_ACCEPTED)).isFalse();
    }

    @Test
    void isEnabledIsTrueForNullTypeOrBlankUser() {
        assertThat(service.isEnabled("student-1", null)).isTrue();
        assertThat(service.isEnabled("", NotificationType.OFFER_ACCEPTED)).isTrue();
    }

    private NotificationPreference pref(String username, NotificationType type, boolean enabled) {
        return NotificationPreference.builder()
            .id("pref_" + type)
            .username(username)
            .type(type)
            .enabled(enabled)
            .build();
    }

    private UserAccount user(String username) {
        return UserAccount.builder()
            .username(username)
            .password("{noop}secret")
            .campusId("campus_demo")
            .role(UserRole.STUDENT)
            .enabled(true)
            .build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
