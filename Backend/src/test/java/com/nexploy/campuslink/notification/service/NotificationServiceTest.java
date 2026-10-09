package com.nexploy.campuslink.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.notification.dto.CreateNotificationRequest;
import com.nexploy.campuslink.notification.dto.NotificationResponse;
import com.nexploy.campuslink.notification.entity.DeliveryAttempt;
import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.repository.DeliveryAttemptRepository;
import com.nexploy.campuslink.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository =
        org.mockito.Mockito.mock(NotificationRepository.class);
    private final DeliveryAttemptRepository deliveryAttemptRepository =
        org.mockito.Mockito.mock(DeliveryAttemptRepository.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final NotificationPreferenceService preferenceService =
        org.mockito.Mockito.mock(NotificationPreferenceService.class);
    private final NotificationService service =
        new NotificationService(notificationRepository, deliveryAttemptRepository, campusAccessService,
            preferenceService);

    @Test
    void createStartsPendingWithNoAttempts() {
        authenticate(user("officer", UserRole.OFFICER, "campus_demo"));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = service.create(new CreateNotificationRequest(
            "student-1", NotificationChannel.EMAIL, "Subject", "Body"));

        assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(response.attemptCount()).isZero();
        assertThat(response.campusId()).isEqualTo("campus_demo");
    }

    @Test
    void recruiterCannotCreate() {
        authenticate(user("rec", UserRole.RECRUITER, "campus_demo"));

        assertThatThrownBy(() -> service.create(new CreateNotificationRequest(
            "student-1", NotificationChannel.SMS, "Subject", "Body")))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recordAttemptIncrementsCountWritesTrailAndSetsStatus() {
        authenticate(user("officer", UserRole.OFFICER, "campus_demo"));
        Notification notification = pending("campus_demo", "student-1");
        when(notificationRepository.findById("ntf_1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = service.recordAttempt("ntf_1", false, "SMTP timeout");

        assertThat(response.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(response.attemptCount()).isEqualTo(1);
        ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
        org.mockito.Mockito.verify(deliveryAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(1);
        assertThat(captor.getValue().getOutcome()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getDetail()).isEqualTo("SMTP timeout");
    }

    @Test
    void recipientCanMarkOwnNotificationRead() {
        authenticate(user("student-1", UserRole.STUDENT, "campus_demo"));
        Notification notification = pending("campus_demo", "student-1");
        when(notificationRepository.findById("ntf_1")).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = service.markRead("ntf_1");

        assertThat(response.status()).isEqualTo(NotificationStatus.READ);
        assertThat(response.readAt()).isNotNull();
    }

    @Test
    void nonRecipientCannotMarkRead() {
        authenticate(user("student-2", UserRole.STUDENT, "campus_demo"));
        when(notificationRepository.findById("ntf_1")).thenReturn(Optional.of(pending("campus_demo", "student-1")));

        assertThatThrownBy(() -> service.markRead("ntf_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonRecipientCannotAccessNotificationOutsideCampus() {
        authenticate(user("rec", UserRole.RECRUITER, "campus_demo"));
        // Recruiter is not the recipient and the notification belongs to another campus.
        when(notificationRepository.findById("ntf_1")).thenReturn(Optional.of(pending("campus_other", "student-9")));

        assertThatThrownBy(() -> service.get("ntf_1"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void recipientFeedIsInAppChannelOnly() {
        authenticate(user("student-1", UserRole.STUDENT, "campus_demo"));
        Notification inApp = Notification.builder()
            .id("ntf_2")
            .campusId("campus_demo")
            .recipientUsername("student-1")
            .channel(NotificationChannel.IN_APP)
            .subject("Subject")
            .body("Body")
            .status(NotificationStatus.SENT)
            .attemptCount(0)
            .createdAt(OffsetDateTime.parse("2026-09-20T09:00:00Z"))
            .build();
        when(notificationRepository.findByRecipientUsernameAndChannel(
            "student-1", NotificationChannel.IN_APP)).thenReturn(java.util.List.of(inApp));

        java.util.List<NotificationResponse> feed = service.list(null);

        assertThat(feed).hasSize(1);
        assertThat(feed.get(0).channel()).isEqualTo(NotificationChannel.IN_APP);
    }

    private Notification pending(String campusId, String recipient) {
        return Notification.builder()
            .id("ntf_1")
            .campusId(campusId)
            .recipientUsername(recipient)
            .channel(NotificationChannel.EMAIL)
            .subject("Subject")
            .body("Body")
            .status(NotificationStatus.PENDING)
            .attemptCount(0)
            .createdAt(OffsetDateTime.parse("2026-09-20T09:00:00Z"))
            .build();
    }

    private UserAccount user(String username, UserRole role, String campusId) {
        return UserAccount.builder()
            .username(username)
            .password("{noop}secret")
            .campusId(campusId)
            .role(role)
            .enabled(true)
            .build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
