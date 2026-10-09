package com.nexploy.campuslink.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.notification.dto.CreateNotificationRequest;
import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.entity.DeliveryAttempt;
import com.nexploy.campuslink.notification.repository.DeliveryAttemptRepository;
import com.nexploy.campuslink.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the notification endpoints. Focuses on the recipient-scope
 * guards from SECURITY.md section 2: a non-privileged user sees and marks-read only notifications
 * addressed to them, while officers/admins create and record delivery attempts. Runs through real
 * routing + the security filter chain against in-memory H2 (the "test" profile).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        deliveryAttemptRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    private Authentication user(UserRole role, String username, String campusId) {
        UserAccount account = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(account, null, account.getAuthorities());
    }

    private Notification seed(String id, String campusId, String recipient, NotificationStatus status) {
        return notificationRepository.save(Notification.builder()
            .id(id).campusId(campusId).recipientUsername(recipient)
            .channel(NotificationChannel.IN_APP).subject("Shortlisted").body("You are shortlisted")
            .status(status).attemptCount(0)
            .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    private Notification seedOutbound(String id, String campusId, String recipient,
            NotificationChannel channel, NotificationStatus status, int attempts) {
        return notificationRepository.save(Notification.builder()
            .id(id).campusId(campusId).recipientUsername(recipient)
            .channel(channel).subject("Interview scheduled").body("Please attend at 10am")
            .status(status).attemptCount(attempts)
            .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void officerCanCreateNotification() throws Exception {
        CreateNotificationRequest request = new CreateNotificationRequest(
            "student1", NotificationChannel.EMAIL, "Interview scheduled", "Please attend at 10am");

        mockMvc.perform(post("/api/notifications")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.recipientUsername").value("student1"))
            .andExpect(jsonPath("$.attemptCount").value(0));
    }

    @Test
    void studentCannotCreateNotification() throws Exception {
        CreateNotificationRequest request = new CreateNotificationRequest(
            "student1", NotificationChannel.EMAIL, "Spoof", "Not allowed");

        mockMvc.perform(post("/api/notifications")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void recipientCanReadTheirOwnNotification() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);

        mockMvc.perform(get("/api/notifications/n1")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recipientUsername").value("student1"));
    }

    @Test
    void otherStudentCannotReadSomeoneElsesNotification() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);

        // student2 is not the recipient and is not privileged -> campus-scope check applies,
        // and even in the same campus a plain recipient scope is enforced via requireAccess.
        mockMvc.perform(get("/api/notifications/n1")
                .with(authentication(user(UserRole.STUDENT, "student2", "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recipientCanMarkOwnNotificationRead() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);

        mockMvc.perform(post("/api/notifications/n1/read")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READ"));
    }

    @Test
    void recordingDeliveryAttemptUpdatesStatusAndCount() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.PENDING);

        mockMvc.perform(post("/api/notifications/n1/attempts")
                .param("succeeded", "true")
                .param("detail", "smtp 250 ok")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.attemptCount").value(1));
    }

    @Test
    void officerCanSearchAdminRosterFilteredByStatusAndChannel() throws Exception {
        seedOutbound("n1", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.FAILED, 3);
        seedOutbound("n2", "campus_a", "student2", NotificationChannel.EMAIL, NotificationStatus.SENT, 1);
        seed("n3", "campus_a", "student3", NotificationStatus.SENT);

        mockMvc.perform(get("/api/notifications/admin")
                .param("status", "FAILED")
                .param("channel", "EMAIL")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("n1"))
            .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    @Test
    void studentCannotSearchAdminRoster() throws Exception {
        mockMvc.perform(get("/api/notifications/admin")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanListDeliveryAttempts() throws Exception {
        seedOutbound("n1", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.FAILED, 1);
        deliveryAttemptRepository.save(DeliveryAttempt.builder()
            .id("a1").notificationId("n1").attemptNumber(1)
            .outcome(NotificationStatus.FAILED).detail("smtp 550")
            .attemptedAt(OffsetDateTime.parse("2026-01-01T10:05:00Z")).build());

        mockMvc.perform(get("/api/notifications/n1/attempts")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].attemptNumber").value(1))
            .andExpect(jsonPath("$[0].outcome").value("FAILED"))
            .andExpect(jsonPath("$[0].detail").value("smtp 550"));
    }

    @Test
    void studentCannotListDeliveryAttempts() throws Exception {
        seedOutbound("n1", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.FAILED, 1);

        mockMvc.perform(get("/api/notifications/n1/attempts")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCanRetryFailedOutboundNotification() throws Exception {
        seedOutbound("n1", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.FAILED, 3);

        mockMvc.perform(post("/api/notifications/n1/retry")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.attemptCount").value(0));
    }

    @Test
    void retryingInAppNotificationIsConflict() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.FAILED);

        mockMvc.perform(post("/api/notifications/n1/retry")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isConflict());
    }

    @Test
    void retryingNonFailedNotificationIsConflict() throws Exception {
        seedOutbound("n1", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.SENT, 1);

        mockMvc.perform(post("/api/notifications/n1/retry")
                .with(authentication(user(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isConflict());
    }

    @Test
    void studentCannotRetryNotification() throws Exception {
        seedOutbound("n1", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.FAILED, 3);

        mockMvc.perform(post("/api/notifications/n1/retry")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void recipientFeedShowsOnlyInAppNotifications() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);
        seedOutbound("n2", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.PENDING, 0);
        seedOutbound("n3", "campus_a", "student1", NotificationChannel.SMS, NotificationStatus.PENDING, 0);

        mockMvc.perform(get("/api/notifications")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("n1"))
            .andExpect(jsonPath("$[0].channel").value("IN_APP"));
    }

    @Test
    void pagedRecipientFeedShowsOnlyInAppNotifications() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);
        seedOutbound("n2", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.SENT, 1);

        mockMvc.perform(get("/api/notifications")
                .param("page", "0")
                .param("size", "20")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value("n1"));
    }

    @Test
    void unreadCountIgnoresOutboundChannels() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);
        seedOutbound("n2", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.PENDING, 0);

        mockMvc.perform(get("/api/notifications/unread-count")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void markAllReadLeavesOutboundRowsUntouched() throws Exception {
        seed("n1", "campus_a", "student1", NotificationStatus.SENT);
        seedOutbound("n2", "campus_a", "student1", NotificationChannel.EMAIL, NotificationStatus.PENDING, 0);

        mockMvc.perform(post("/api/notifications/read-all")
                .with(authentication(user(UserRole.STUDENT, "student1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(1));

        // The email delivery row keeps its state; only the in-app row was marked read.
        assertThat(notificationRepository.findById("n2").orElseThrow().getStatus())
            .isEqualTo(NotificationStatus.PENDING);
    }
}
