package com.nexploy.campuslink.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.DocumentType;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for {@link NotificationReminderService}. It verifies each sweep resolves the student
 * recipient, uses the correct {@link NotificationType} + stable idempotent {@code sourceEventId},
 * and creates nothing when the recipient can't be resolved. The actual idempotency/opt-out gate
 * lives in {@link NotificationService#createFromEvent} and is covered by its own tests.
 */
@ExtendWith(MockitoExtension.class)
class NotificationReminderServiceTest {

    @Mock
    private InterviewRepository interviewRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private RecruiterRepository recruiterRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationReminderService service;

    private static final OffsetDateTime SOON = OffsetDateTime.parse("2026-01-01T10:00:00Z");

    @Test
    void interviewReminderNotifiesResolvedStudentWithStableSourceId() {
        when(interviewRepository.findByStatusAndScheduledAtBetween(
                eq(InterviewStatus.SCHEDULED), any(), any()))
            .thenReturn(List.of(Interview.builder()
                .id("int_1").campusId("campus_a").studentId("stu_1")
                .status(InterviewStatus.SCHEDULED).companyName("Acme").scheduledAt(SOON).build()));
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));
        when(userAccountRepository.existsById("student@uni.edu")).thenReturn(true);

        service.remindUpcomingInterviews();

        verify(notificationService).createFromEvent(
            eq("interview-reminder:int_1"), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.INTERVIEW_REMINDER), eq("int_1"),
            eq("Interview Reminder with Acme"),
            eq("Reminder: your interview with Acme is coming up on 2026-01-01T10:00Z. "
                + "Please make sure you are prepared."));
    }

    @Test
    void interviewReminderForUnknownStudentCreatesNothing() {
        when(interviewRepository.findByStatusAndScheduledAtBetween(
                eq(InterviewStatus.SCHEDULED), any(), any()))
            .thenReturn(List.of(Interview.builder()
                .id("int_1").campusId("campus_a").studentId("ghost")
                .status(InterviewStatus.SCHEDULED).scheduledAt(SOON).build()));
        when(studentRepository.findById("ghost")).thenReturn(Optional.empty());

        service.remindUpcomingInterviews();

        verify(notificationService, never()).createFromEvent(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void interviewReminderForStudentWithoutLoginAccountCreatesNothing() {
        // The student row exists but has no user_accounts entry (e.g. a demo/seed profile). Its email
        // must not reach createFromEvent: in Postgres notifications.recipient_username is FK'd to
        // user_accounts(username), so the insert would violate the FK and abort the whole scheduled sweep.
        when(interviewRepository.findByStatusAndScheduledAtBetween(
                eq(InterviewStatus.SCHEDULED), any(), any()))
            .thenReturn(List.of(Interview.builder()
                .id("int_1").campusId("campus_a").studentId("stu_1")
                .status(InterviewStatus.SCHEDULED).companyName("Acme").scheduledAt(SOON).build()));
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("orphan@uni.edu").build()));
        when(userAccountRepository.existsById("orphan@uni.edu")).thenReturn(false);

        service.remindUpcomingInterviews();

        verify(notificationService, never()).createFromEvent(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void imminentInterviewReminderNotifiesStudentWithDistinctSourceId() {
        when(interviewRepository.findByStatusAndScheduledAtBetween(
                eq(InterviewStatus.SCHEDULED), any(), any()))
            .thenReturn(List.of(Interview.builder()
                .id("int_1").campusId("campus_a").studentId("stu_1")
                .status(InterviewStatus.SCHEDULED).companyName("Acme").scheduledAt(SOON).build()));
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));
        when(userAccountRepository.existsById("student@uni.edu")).thenReturn(true);

        service.remindImminentInterviews();

        verify(notificationService).createFromEvent(
            eq("interview-reminder-soon:int_1"), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.INTERVIEW_STARTING_SOON), eq("int_1"),
            eq("Interview Starting Soon with Acme"),
            eq("Your interview with Acme starts within the hour on 2026-01-01T10:00Z. Get ready!"));
    }

    @Test
    void offerDeadlineReminderNotifiesResolvedStudentWithStableSourceId() {
        LocalDate deadline = LocalDate.now().plusDays(1);
        when(offerRepository.findByStatusAndAcceptanceDeadlineBetween(
                eq(OfferStatus.EXTENDED), any(), any()))
            .thenReturn(List.of(Offer.builder()
                .id("offer_1").campusId("campus_a").studentId("stu_1").recruiterId("rec_1")
                .role("SDE").companyName("Acme").status(OfferStatus.EXTENDED)
                .acceptanceDeadline(deadline).build()));
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));
        when(userAccountRepository.existsById("student@uni.edu")).thenReturn(true);

        service.remindOfferDeadlines();

        verify(notificationService).createFromEvent(
            eq("offer-deadline:offer_1"), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.OFFER_DEADLINE), eq("offer_1"),
            eq("Offer Response Due Soon with Acme"),
            eq("Your offer with Acme is awaiting your response. Please respond by " + deadline + "."));
    }

    @Test
    void offerDeadlineReminderForUnknownStudentCreatesNothing() {
        when(offerRepository.findByStatusAndAcceptanceDeadlineBetween(
                eq(OfferStatus.EXTENDED), any(), any()))
            .thenReturn(List.of(Offer.builder()
                .id("offer_1").campusId("campus_a").studentId("ghost").status(OfferStatus.EXTENDED)
                .acceptanceDeadline(LocalDate.now()).build()));
        when(studentRepository.findById("ghost")).thenReturn(Optional.empty());

        service.remindOfferDeadlines();

        verify(notificationService, never()).createFromEvent(
            any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pendingDocumentReminderNotifiesStudentAndCampusOfficerDigest() {
        when(documentRepository.findByReviewStatusAndUploadedAtBefore(eq(ReviewStatus.PENDING), any()))
            .thenReturn(List.of(Document.builder()
                .id("doc_1").campusId("campus_a").studentId("stu_1")
                .type(DocumentType.MARKSHEET).reviewStatus(ReviewStatus.PENDING)
                .uploadedAt(SOON).build()));
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));
        when(userAccountRepository.existsById("student@uni.edu")).thenReturn(true);
        when(userAccountRepository.findByRoleAndCampusIdAndEnabledTrue(UserRole.OFFICER, "campus_a"))
            .thenReturn(List.of(UserAccount.builder().username("officer@uni.edu").build()));

        service.remindPendingDocuments();

        // Student gets a per-document nudge keyed on the document id.
        verify(notificationService).createFromEvent(
            eq("document-pending:doc_1"), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.DOCUMENT_REMINDER), eq("doc_1"),
            eq("Document Awaiting Review"),
            eq("Your Marksheet document has been awaiting review for more than 24 hours."));
        // Officer gets the campus-level daily digest (source id carries the day bucket).
        verify(notificationService).createFromEvent(
            eq("document-review-pending:campus_a:" + LocalDate.now().toEpochDay()),
            eq("campus_a"), eq("officer@uni.edu"),
            eq(NotificationType.DOCUMENT_REVIEW_PENDING), eq("campus_a"),
            eq("Documents Pending Review"),
            eq("1 document(s) are pending review across your campus."));
    }

    @Test
    void offerFollowupNotifiesResolvedRecruiterWithOneShotSourceId() {
        when(offerRepository.findByStatusAndExtendedAtBefore(eq(OfferStatus.EXTENDED), any()))
            .thenReturn(List.of(Offer.builder()
                .id("offer_1").campusId("campus_a").studentId("stu_1").recruiterId("rec_1")
                .companyName("Acme").status(OfferStatus.EXTENDED).extendedAt(SOON).build()));
        when(recruiterRepository.findById("rec_1"))
            .thenReturn(Optional.of(Recruiter.builder().id("rec_1").email("recruiter@acme.com").build()));
        when(userAccountRepository.existsById("recruiter@acme.com")).thenReturn(true);

        service.remindRecruitersOfPendingOffers();

        verify(notificationService).createFromEvent(
            eq("offer-followup:offer_1"), eq("campus_a"), eq("recruiter@acme.com"),
            eq(NotificationType.OFFER_FOLLOWUP), eq("offer_1"),
            eq("Offer Awaiting Response with Acme"),
            eq("Your offer with Acme (reference: offer_1) is still awaiting the candidate's response. "
                + "Consider following up."));
    }

    @Test
    void offerFollowupForUnknownRecruiterCreatesNothing() {
        when(offerRepository.findByStatusAndExtendedAtBefore(eq(OfferStatus.EXTENDED), any()))
            .thenReturn(List.of(Offer.builder()
                .id("offer_1").campusId("campus_a").recruiterId("ghost")
                .status(OfferStatus.EXTENDED).extendedAt(SOON).build()));
        when(recruiterRepository.findById("ghost")).thenReturn(Optional.empty());

        service.remindRecruitersOfPendingOffers();

        verify(notificationService, never()).createFromEvent(
            any(), any(), any(), any(), any(), any(), any());
    }
}
