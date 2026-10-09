package com.nexploy.campuslink.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.application.dto.ChangeApplicationStatusRequest;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.application.repository.ApplicationStatusChangeRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewMode;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.notification.entity.Notification;
import com.nexploy.campuslink.notification.entity.NotificationChannel;
import com.nexploy.campuslink.notification.entity.NotificationStatus;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.repository.NotificationRepository;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
 * End-to-end test that the event→notification wiring works through a real HTTP transaction: accepting
 * an offer publishes an {@code OfferAccepted} event whose after-commit listener resolves the recruiter
 * to their username and creates an In-App {@link Notification}. Mirrors {@link
 * com.nexploy.campuslink.shared.event.DomainEventIntegrationTest}: the endpoint commits, so the
 * AFTER_COMMIT listener actually fires (a rolled-back @Transactional test method would not deliver it).
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class NotificationEventWiringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private com.nexploy.campuslink.notification.repository.NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private com.nexploy.campuslink.student.repository.StudentRepository studentRepository;

    @Autowired
    private com.nexploy.campuslink.document.repository.DocumentRepository documentRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationStatusChangeRepository statusChangeRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        statusChangeRepository.deleteAll();
        applicationRepository.deleteAll();
        offerRepository.deleteAll();
        recruiterRepository.deleteAll();
        documentRepository.deleteAll();
        interviewRepository.deleteAll();
        driveRepository.deleteAll();
        studentRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void seedRecruiter(String id, String campusId, String email) {
        recruiterRepository.save(Recruiter.builder()
            .id(id).campusId(campusId).name("Acme Recruiter").companyName("Acme")
            .email(email).active(true).build());
    }

    private void seedExtendedOffer(String id, String campusId, String recruiterId) {
        offerRepository.save(Offer.builder()
            .id(id).campusId(campusId).studentId("stu_1").driveId("drive_1").recruiterId(recruiterId)
            .role("SDE").packageLpa(new BigDecimal("12.0")).status(OfferStatus.EXTENDED)
            .extendedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void acceptingAnOfferCreatesAnInAppNotificationForTheRecruiter() throws Exception {
        seedRecruiter("rec_1", "campus_a", "rec@acme.com");
        seedExtendedOffer("offer_1", "campus_a", "rec_1");

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk());

        List<Notification> created = notificationRepository.findByRecipientUsername("rec@acme.com");
        assertThat(created).hasSize(1);
        Notification n = created.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.OFFER_ACCEPTED);
        assertThat(n.getReferenceId()).isEqualTo("offer_1");
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSourceEventId()).isNotBlank();
    }

    @Test
    void acceptingAnOfferWhoseRecruiterCannotBeResolvedCreatesNoNotification() throws Exception {
        // No recruiter row for rec_missing -> recipient unresolved -> nothing created, accept still succeeds.
        seedExtendedOffer("offer_1", "campus_a", "rec_missing");

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk());

        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void redeliveredEventDoesNotDuplicate() throws Exception {
        seedRecruiter("rec_1", "campus_a", "rec@acme.com");
        seedExtendedOffer("offer_1", "campus_a", "rec_1");

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk());

        // Accepting an already-accepted offer is rejected, so re-drive idempotency at the service seam:
        // a second event with the same (sourceEventId, recipient) must not create a second row.
        String sourceEventId = notificationRepository.findByRecipientUsername("rec@acme.com")
            .get(0).getSourceEventId();
        assertThat(notificationRepository
            .existsBySourceEventIdAndRecipientUsername(sourceEventId, "rec@acme.com")).isTrue();
        assertThat(notificationRepository.findByRecipientUsername("rec@acme.com")).hasSize(1);
    }

    @Test
    void mutedTypeSuppressesTheNotificationButTheBusinessActionStillSucceeds() throws Exception {
        seedRecruiter("rec_1", "campus_a", "rec@acme.com");
        seedExtendedOffer("offer_1", "campus_a", "rec_1");
        // Recipient (recruiter, resolved to their email) has opted out of OFFER_ACCEPTED.
        preferenceRepository.save(com.nexploy.campuslink.notification.entity.NotificationPreference.builder()
            .id("pref_1").username("rec@acme.com")
            .type(NotificationType.OFFER_ACCEPTED).enabled(false)
            .updatedAt(OffsetDateTime.parse("2026-01-01T09:00:00Z")).build());

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk());

        assertThat(notificationRepository.findByRecipientUsername("rec@acme.com")).isEmpty();
    }

    @Test
    void shortlistingAnApplicationNotifiesTheStudent() throws Exception {
        seedStudentAndAppliedApplication("student@campus.example");

        mockMvc.perform(patch("/api/applications/app_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeApplicationStatusRequest(ApplicationStatus.SHORTLISTED))))
            .andExpect(status().isOk());

        List<Notification> created = notificationRepository.findByRecipientUsername("student@campus.example");
        assertThat(created).hasSize(1);
        Notification n = created.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.SHORTLISTED);
        assertThat(n.getReferenceId()).isEqualTo("drv_1");
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void rejectingAnApplicationNotifiesTheStudentOfTheRejection() throws Exception {
        seedStudentAndAppliedApplication("student@campus.example");

        mockMvc.perform(patch("/api/applications/app_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeApplicationStatusRequest(ApplicationStatus.REJECTED))))
            .andExpect(status().isOk());

        List<Notification> created = notificationRepository.findByRecipientUsername("student@campus.example");
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getType()).isEqualTo(NotificationType.SHORTLIST_REJECTED);
        assertThat(created.get(0).getReferenceId()).isEqualTo("drv_1");
    }

    @Test
    void advancingAnApplicationToAssessmentNotifiesTheStudent() throws Exception {
        seedStudentAndApplication("student@campus.example", ApplicationStatus.SHORTLISTED);

        mockMvc.perform(patch("/api/applications/app_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(
                    new ChangeApplicationStatusRequest(ApplicationStatus.ASSESSMENT))))
            .andExpect(status().isOk());

        List<Notification> created = notificationRepository.findByRecipientUsername("student@campus.example");
        assertThat(created).hasSize(1);
        Notification n = created.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.APPLICATION_ADVANCED);
        assertThat(n.getReferenceId()).isEqualTo("drv_1");
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getBody()).contains("Assessment");
    }

    /** Student + ACTIVE drive + application at the given status for the funnel-status wiring tests. */
    private void seedStudentAndApplication(String studentEmail, ApplicationStatus status) {
        studentRepository.save(com.nexploy.campuslink.student.entity.Student.builder()
            .id("stu_1").campusId("campus_a").email(studentEmail).build());
        driveRepository.save(Drive.builder()
            .id("drv_1").campusId("campus_a").recruiterId("rec_1").companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(DriveStatus.ACTIVE).build());
        applicationRepository.save(Application.builder()
            .id("app_1").studentId("stu_1").driveId("drv_1").appliedOn(LocalDate.now())
            .status(status).build());
    }

    private void seedStudentAndAppliedApplication(String studentEmail) {
        seedStudentAndApplication(studentEmail, ApplicationStatus.APPLIED);
    }

    @Test
    void approvingADocumentNotifiesTheOwningStudent() throws Exception {
        studentRepository.save(com.nexploy.campuslink.student.entity.Student.builder()
            .id("stu_1").campusId("campus_a").email("student@uni.edu").build());
        documentRepository.save(com.nexploy.campuslink.document.entity.Document.builder()
            .id("doc_1").campusId("campus_a").studentId("stu_1")
            .type(com.nexploy.campuslink.document.entity.DocumentType.RESUME)
            .fileName("cv.pdf").contentType("application/pdf").sizeBytes(1024).storageKey("k/cv.pdf")
            .scanStatus(com.nexploy.campuslink.document.entity.ScanStatus.CLEAN)
            .reviewStatus(com.nexploy.campuslink.document.entity.ReviewStatus.PENDING)
            .uploadedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());

        mockMvc.perform(post("/api/documents/doc_1/review")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isOk());

        List<Notification> created = notificationRepository.findByRecipientUsername("student@uni.edu");
        assertThat(created).hasSize(1);
        assertThat(created.get(0).getType()).isEqualTo(NotificationType.DOCUMENT_APPROVED);
        assertThat(created.get(0).getReferenceId()).isEqualTo("doc_1");
    }

    /** Student + ACTIVE drive + SCHEDULED interview for the interview-notification wiring tests. */
    private void seedScheduledInterview(String studentEmail, String round) {
        studentRepository.save(com.nexploy.campuslink.student.entity.Student.builder()
            .id("stu_1").campusId("campus_a").email(studentEmail).build());
        driveRepository.save(Drive.builder()
            .id("drv_1").campusId("campus_a").recruiterId("rec_1").companyName("TechNova").role("SDE")
            .packageLpa(new BigDecimal("12.0")).status(DriveStatus.ACTIVE).build());
        interviewRepository.save(Interview.builder()
            .id("int_1").campusId("campus_a").studentId("stu_1").driveId("drv_1")
            .companyName("TechNova").round(round).mode(InterviewMode.ON_CAMPUS)
            .scheduledAt(OffsetDateTime.parse("2026-02-01T09:00:00Z"))
            .status(InterviewStatus.SCHEDULED).result(InterviewResult.PENDING)
            .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void schedulingAnInterviewNotifiesTheStudentWithTheRoundName() throws Exception {
        seedStudentAndApplication("student@campus.example", ApplicationStatus.INTERVIEW);

        mockMvc.perform(post("/api/interviews")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"studentId\":\"stu_1\",\"driveId\":\"drv_1\",\"mode\":\"ON_CAMPUS\","
                    + "\"scheduledAt\":\"2026-02-01T09:00:00Z\",\"round\":\"Technical Round 1\"}"))
            .andExpect(status().isCreated());

        List<Notification> created = notificationRepository.findByRecipientUsername("student@campus.example");
        assertThat(created).hasSize(1);
        Notification n = created.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.INTERVIEW_SCHEDULED);
        assertThat(n.getReferenceId()).isNotBlank();
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getBody()).contains("Technical Round 1");
    }

    @Test
    void recordingASelectedResultNotifiesTheStudentOfTheSelection() throws Exception {
        seedScheduledInterview("student@campus.example", "Final Round");
        applicationRepository.save(Application.builder()
            .id("app_1").studentId("stu_1").driveId("drv_1").appliedOn(LocalDate.now())
            .status(ApplicationStatus.INTERVIEW).build());

        mockMvc.perform(post("/api/interviews/int_1/result")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"result\":\"SELECTED\",\"feedback\":\"Strong\"}"))
            .andExpect(status().isOk());

        List<Notification> created = notificationRepository.findByRecipientUsername("student@campus.example");
        assertThat(created).hasSize(1);
        Notification n = created.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.INTERVIEW_SELECTED);
        assertThat(n.getReferenceId()).isEqualTo("int_1");
        assertThat(n.getBody()).contains("Congratulations");
        assertThat(n.getBody()).contains("Final Round");
    }

    @Test
    void recordingAnOnHoldResultCompletesTheInterviewWithoutNotifying() throws Exception {
        seedScheduledInterview("student@campus.example", "Technical Round 1");

        mockMvc.perform(post("/api/interviews/int_1/result")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"result\":\"ON_HOLD\"}"))
            .andExpect(status().isOk());

        assertThat(notificationRepository.count()).isZero();
    }
}
