package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.mentor.entity.MentorAssignment;
import com.nexploy.campuslink.mentor.repository.MentorAssignmentRepository;
import com.nexploy.campuslink.application.event.ApplicationAdvancedEvent;
import com.nexploy.campuslink.document.event.DocumentReviewedEvent;
import com.nexploy.campuslink.interview.event.InterviewCancelledEvent;
import com.nexploy.campuslink.interview.event.InterviewRescheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent;
import com.nexploy.campuslink.interview.event.InterviewScheduledEvent;
import com.nexploy.campuslink.matching.event.ShortlistDecidedEvent;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.template.NotificationTemplate;
import com.nexploy.campuslink.notification.template.RenderedNotification;
import com.nexploy.campuslink.offer.event.OfferAcceptedEvent;
import com.nexploy.campuslink.offer.event.OfferDeclinedEvent;
import com.nexploy.campuslink.offer.event.OfferExtendedEvent;
import com.nexploy.campuslink.offer.event.OfferRevokedEvent;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.risk.event.InterventionAssignedEvent;
import com.nexploy.campuslink.risk.event.StudentRiskDetectedEvent;
import com.nexploy.campuslink.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a domain event into an in-app notification — the shared handler logic used by both event
 * transports. The in-JVM {@link NotificationEventListener} (Kafka off) and the
 * {@link KafkaNotificationConsumer} (Kafka on) both delegate here, so the mapping from event to
 * notification lives in exactly one place regardless of how the event was delivered.
 *
 * <p>Recipient resolution is deliberate: an event carries domain ids (studentId, recruiterId), which
 * these handlers resolve to the recipient's username (email == username) before handing a fully
 * resolved recipient to the notification service. Events whose recipient cannot be resolved create
 * nothing rather than failing. Creation is idempotent per (source event, recipient) via
 * {@link NotificationService#createFromEvent}, so a redelivered Kafka message is harmless.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventHandlers {

    private final NotificationService notificationService;
    private final StudentRepository studentRepository;
    private final RecruiterRepository recruiterRepository;
    private final MentorAssignmentRepository mentorAssignmentRepository;

    /** A new offer was extended → tell the student. */
    public void onOfferExtended(OfferExtendedEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.OFFER_EXTENDED, event.offerId(),
                java.util.Map.of("role", event.role())));
    }

    /** A student accepted their offer → tell the recruiter who made it. */
    public void onOfferAccepted(OfferAcceptedEvent event) {
        recruiterUsername(event.recruiterId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.OFFER_ACCEPTED, event.offerId(),
                java.util.Map.of("offerId", event.offerId())));
    }

    /** A student declined their offer → tell the recruiter who made it. */
    public void onOfferDeclined(OfferDeclinedEvent event) {
        recruiterUsername(event.recruiterId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.OFFER_DECLINED, event.offerId(),
                java.util.Map.of("offerId", event.offerId())));
    }

    /** An officer revoked an offer → tell the student it was extended to. */
    public void onOfferRevoked(OfferRevokedEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.OFFER_REVOKED, event.offerId(), java.util.Map.of()));
    }

    /** A shortlist decision was recorded → tell the student whether they progressed. */
    public void onShortlistDecided(ShortlistDecidedEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                event.rejected() ? NotificationType.SHORTLIST_REJECTED : NotificationType.SHORTLISTED,
                event.driveId(), java.util.Map.of()));
    }

    /** An application advanced through the funnel → tell the student which stage they reached. */
    public void onApplicationAdvanced(ApplicationAdvancedEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.APPLICATION_ADVANCED, event.driveId(),
                java.util.Map.of("stage", stageLabel(event.newStatus()))));
    }

    /** An interview was scheduled → tell the student. */
    public void onInterviewScheduled(InterviewScheduledEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.INTERVIEW_SCHEDULED, event.interviewId(),
                interviewVars(event.round(), event.companyName(), event.scheduledAt())));
    }

    /** An interview was rescheduled → tell the student the new time. */
    public void onInterviewRescheduled(InterviewRescheduledEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.INTERVIEW_RESCHEDULED, event.interviewId(),
                interviewVars(event.round(), event.companyName(), event.scheduledAt())));
    }

    /** An interview was cancelled → tell the student. */
    public void onInterviewCancelled(InterviewCancelledEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                NotificationType.INTERVIEW_CANCELLED, event.interviewId(),
                interviewVars(event.round(), event.companyName(), null)));
    }

    /** An interview outcome was recorded → tell the student whether they were selected. */
    public void onInterviewResultRecorded(InterviewResultRecordedEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                event.selected() ? NotificationType.INTERVIEW_SELECTED : NotificationType.INTERVIEW_REJECTED,
                event.interviewId(),
                java.util.Map.of("company", companyClause(event.companyName()),
                    "round", roundClause(event.round()))));
    }

    /** A document was reviewed → tell the owning student whether it was approved. */
    public void onDocumentReviewed(DocumentReviewedEvent event) {
        studentUsername(event.studentId()).ifPresent(recipient ->
            notify(event.eventId(), event.campusId(), recipient,
                event.approved() ? NotificationType.DOCUMENT_APPROVED : NotificationType.DOCUMENT_REJECTED,
                event.documentId(), java.util.Map.of()));
    }

    /** An intervention plan was raised → tell the assigned mentor. */
    public void onInterventionAssigned(InterventionAssignedEvent event) {
        notify(event.eventId(), event.campusId(), event.mentorUsername(),
            NotificationType.INTERVENTION_ASSIGNED, event.planId(),
            java.util.Map.of("studentId", event.studentId()));
    }

    /**
     * A student was flagged as high-support-need → tell the student's assigned mentor(s). The student
     * is deliberately not notified (support-oriented, never a "you are at risk" message). If the
     * student has no assigned mentor, nothing is created.
     */
    public void onStudentRiskDetected(StudentRiskDetectedEvent event) {
        for (MentorAssignment assignment : mentorAssignmentRepository.findByStudentId(event.studentId())) {
            notify(event.eventId(), event.campusId(), assignment.getMentorUsername(),
                NotificationType.RISK_DETECTED, event.studentId(),
                java.util.Map.of("studentId", event.studentId(), "band", event.band()));
        }
    }

    /**
     * Renders the template for {@code type} with {@code variables} and hands the resolved subject +
     * body to the notification service. Centralises the render-then-dispatch step every handler shares.
     */
    private void notify(String sourceEventId, String campusId, String recipient,
                        NotificationType type, String referenceId, java.util.Map<String, String> variables) {
        RenderedNotification message = NotificationTemplate.forType(type).render(variables);
        notificationService.createFromEvent(
            sourceEventId, campusId, recipient, type, referenceId, message.subject(), message.body());
    }

    private java.util.Optional<String> studentUsername(String studentId) {
        return studentRepository.findById(studentId)
            .map(com.nexploy.campuslink.student.entity.Student::getEmail);
    }

    private java.util.Optional<String> recruiterUsername(String recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .map(com.nexploy.campuslink.recruiter.entity.Recruiter::getEmail);
    }

    /** "ASSESSMENT" → "Assessment" — a human-friendly stage name for the advanced template. */
    private static String stageLabel(String status) {
        if (status == null || status.isBlank()) {
            return "next";
        }
        return status.substring(0, 1) + status.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Builds the {round}/{company}/{time} clause variables for the interview templates. */
    private static java.util.Map<String, String> interviewVars(
            String round, String companyName, java.time.OffsetDateTime scheduledAt) {
        return java.util.Map.of(
            "round", roundClause(round),
            "company", companyClause(companyName),
            "time", timeClause(scheduledAt));
    }

    /** " with Acme" when a company is known, else "" — keeps the message natural either way. */
    private static String companyClause(String companyName) {
        return (companyName == null || companyName.isBlank()) ? "" : " with " + companyName;
    }

    /** " Technical Round 1" when a round is named, else "" — unnamed rounds read as before. */
    private static String roundClause(String round) {
        return (round == null || round.isBlank()) ? "" : " " + round;
    }

    /** " on 2026-01-01T10:00Z" when a time is known, else "" (e.g. a cancel carries none). */
    private static String timeClause(java.time.OffsetDateTime scheduledAt) {
        return scheduledAt == null ? "" : " on " + scheduledAt;
    }
}
