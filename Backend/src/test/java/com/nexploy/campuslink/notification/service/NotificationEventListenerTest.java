package com.nexploy.campuslink.notification.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.mentor.entity.MentorAssignment;
import com.nexploy.campuslink.mentor.repository.MentorAssignmentRepository;
import com.nexploy.campuslink.document.event.DocumentReviewedEvent;
import com.nexploy.campuslink.interview.event.InterviewCancelledEvent;
import com.nexploy.campuslink.interview.event.InterviewRescheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewScheduledEvent;
import com.nexploy.campuslink.matching.event.ShortlistDecidedEvent;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.offer.event.OfferAcceptedEvent;
import com.nexploy.campuslink.offer.event.OfferDeclinedEvent;
import com.nexploy.campuslink.offer.event.OfferExtendedEvent;
import com.nexploy.campuslink.offer.event.OfferRevokedEvent;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.risk.event.InterventionAssignedEvent;
import com.nexploy.campuslink.risk.event.StudentRiskDetectedEvent;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the recipient-resolution and dispatch logic in {@link NotificationEventListener}.
 * It verifies each handler resolves the correct recipient (studentId/recruiterId → username) and
 * hands the right {@link NotificationType} + reference id to {@link NotificationService}, and that
 * an unresolvable recipient creates nothing. The AFTER_COMMIT / REQUIRES_NEW transaction wiring is
 * exercised by {@link com.nexploy.campuslink.notification.controller.NotificationEventWiringIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private RecruiterRepository recruiterRepository;
    @Mock
    private MentorAssignmentRepository mentorAssignmentRepository;

    @InjectMocks
    private NotificationEventHandlers listener;

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-01-01T10:00:00Z");

    @Test
    void offerExtendedNotifiesResolvedStudent() {
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));

        OfferExtendedEvent event =
            OfferExtendedEvent.of("campus_a", "offer_1", "stu_1", "rec_1", "SDE", NOW);
        listener.onOfferExtended(event);

        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.OFFER_EXTENDED), eq("offer_1"),
            eq("New Offer Extended — SDE"),
            eq("Great news! You have received an offer for the SDE position. "
                + "Please review the offer details and respond at your earliest convenience."));
    }

    @Test
    void offerExtendedForUnknownStudentCreatesNothing() {
        when(studentRepository.findById("ghost")).thenReturn(Optional.empty());

        listener.onOfferExtended(
            OfferExtendedEvent.of("campus_a", "offer_1", "ghost", "rec_1", "SDE", NOW));

        verify(notificationService, never()).createFromEvent(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void offerAcceptedNotifiesResolvedRecruiter() {
        when(recruiterRepository.findById("rec_1"))
            .thenReturn(Optional.of(Recruiter.builder().id("rec_1").email("rec@acme.com").build()));

        OfferAcceptedEvent event =
            OfferAcceptedEvent.of("campus_a", "offer_1", "stu_1", "rec_1", NOW);
        listener.onOfferAccepted(event);

        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("rec@acme.com"),
            eq(NotificationType.OFFER_ACCEPTED), eq("offer_1"),
            eq("Offer Accepted — Reference offer_1"),
            eq("A candidate has accepted your offer (reference: offer_1). "
                + "You can now proceed with the next steps in your hiring pipeline."));
    }

    @Test
    void interventionAssignedNotifiesMentorDirectly() {
        InterventionAssignedEvent event =
            InterventionAssignedEvent.of("campus_a", "plan_1", "stu_1", "mentor@uni.edu", NOW);
        listener.onInterventionAssigned(event);

        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("mentor@uni.edu"),
            eq(NotificationType.INTERVENTION_ASSIGNED), eq("plan_1"),
            eq("Intervention Plan Assigned"),
            eq("An intervention plan has been assigned to you for student stu_1. "
                + "Please review it at your earliest convenience."));
    }

    @Test
    void riskDetectedNotifiesEveryAssignedMentorButNotTheStudent() {
        when(mentorAssignmentRepository.findByStudentId("stu_1")).thenReturn(List.of(
            MentorAssignment.builder().mentorUsername("mentor1@uni.edu").studentId("stu_1").build(),
            MentorAssignment.builder().mentorUsername("mentor2@uni.edu").studentId("stu_1").build()));

        StudentRiskDetectedEvent event =
            StudentRiskDetectedEvent.of("campus_a", "stu_1", "risk_1", "HIGH", 0.82, NOW);
        listener.onStudentRiskDetected(event);

        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("mentor1@uni.edu"),
            eq(NotificationType.RISK_DETECTED), eq("stu_1"),
            eq("Student Needs Support — HIGH Band"),
            eq("Student stu_1 has been flagged in the HIGH support band. Consider reaching out."));
        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("mentor2@uni.edu"),
            eq(NotificationType.RISK_DETECTED), eq("stu_1"),
            eq("Student Needs Support — HIGH Band"),
            eq("Student stu_1 has been flagged in the HIGH support band. Consider reaching out."));
    }

    @Test
    void riskDetectedForStudentWithoutMentorCreatesNothing() {
        when(mentorAssignmentRepository.findByStudentId("stu_1")).thenReturn(List.of());

        listener.onStudentRiskDetected(
            StudentRiskDetectedEvent.of("campus_a", "stu_1", "risk_1", "HIGH", 0.82, NOW));

        verify(notificationService, never()).createFromEvent(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void offerDeclinedNotifiesRecruiter() {
        when(recruiterRepository.findById("rec_1"))
            .thenReturn(Optional.of(Recruiter.builder().id("rec_1").email("rec@acme.com").build()));

        OfferDeclinedEvent event =
            OfferDeclinedEvent.of("campus_a", "offer_1", "stu_1", "rec_1", NOW);
        listener.onOfferDeclined(event);

        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("rec@acme.com"),
            eq(NotificationType.OFFER_DECLINED), eq("offer_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void offerRevokedNotifiesStudent() {
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));

        OfferRevokedEvent event = OfferRevokedEvent.of("campus_a", "offer_1", "stu_1", NOW);
        listener.onOfferRevoked(event);

        verify(notificationService).createFromEvent(
            eq(event.eventId()), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.OFFER_REVOKED), eq("offer_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shortlistDecidedUsesTheRightTypePerDecision() {
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));

        ShortlistDecidedEvent shortlisted =
            ShortlistDecidedEvent.of("campus_a", "drive_1", "stu_1", false, NOW);
        listener.onShortlistDecided(shortlisted);
        verify(notificationService).createFromEvent(
            eq(shortlisted.eventId()), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.SHORTLISTED), eq("drive_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        ShortlistDecidedEvent rejected =
            ShortlistDecidedEvent.of("campus_a", "drive_1", "stu_1", true, NOW);
        listener.onShortlistDecided(rejected);
        verify(notificationService).createFromEvent(
            eq(rejected.eventId()), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.SHORTLIST_REJECTED), eq("drive_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void interviewLifecycleNotifiesStudentWithCorrectTypes() {
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));

        listener.onInterviewScheduled(
            InterviewScheduledEvent.of("campus_a", "int_1", "stu_1", null, "Acme", NOW, NOW));
        listener.onInterviewRescheduled(
            InterviewRescheduledEvent.of("campus_a", "int_1", "stu_1", null, "Acme", NOW, NOW));
        listener.onInterviewCancelled(
            InterviewCancelledEvent.of("campus_a", "int_1", "stu_1", null, "Acme", NOW));

        verify(notificationService).createFromEvent(
            org.mockito.ArgumentMatchers.anyString(), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.INTERVIEW_SCHEDULED), eq("int_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(notificationService).createFromEvent(
            org.mockito.ArgumentMatchers.anyString(), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.INTERVIEW_RESCHEDULED), eq("int_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(notificationService).createFromEvent(
            org.mockito.ArgumentMatchers.anyString(), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.INTERVIEW_CANCELLED), eq("int_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void documentReviewedUsesTheRightTypePerDecision() {
        when(studentRepository.findById("stu_1"))
            .thenReturn(Optional.of(Student.builder().id("stu_1").email("student@uni.edu").build()));

        listener.onDocumentReviewed(
            DocumentReviewedEvent.of("campus_a", "doc_1", "stu_1", true, NOW));
        verify(notificationService).createFromEvent(
            org.mockito.ArgumentMatchers.anyString(), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.DOCUMENT_APPROVED), eq("doc_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        listener.onDocumentReviewed(
            DocumentReviewedEvent.of("campus_a", "doc_1", "stu_1", false, NOW));
        verify(notificationService).createFromEvent(
            org.mockito.ArgumentMatchers.anyString(), eq("campus_a"), eq("student@uni.edu"),
            eq(NotificationType.DOCUMENT_REJECTED), eq("doc_1"),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
