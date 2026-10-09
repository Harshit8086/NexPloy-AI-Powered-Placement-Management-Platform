package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.application.event.ApplicationAdvancedEvent;
import com.nexploy.campuslink.document.event.DocumentReviewedEvent;
import com.nexploy.campuslink.interview.event.InterviewCancelledEvent;
import com.nexploy.campuslink.interview.event.InterviewRescheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewResultRecordedEvent;
import com.nexploy.campuslink.interview.event.InterviewScheduledEvent;
import com.nexploy.campuslink.matching.event.ShortlistDecidedEvent;
import com.nexploy.campuslink.offer.event.OfferAcceptedEvent;
import com.nexploy.campuslink.offer.event.OfferDeclinedEvent;
import com.nexploy.campuslink.offer.event.OfferExtendedEvent;
import com.nexploy.campuslink.offer.event.OfferRevokedEvent;
import com.nexploy.campuslink.risk.event.InterventionAssignedEvent;
import com.nexploy.campuslink.risk.event.StudentRiskDetectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

/**
 * In-process (Kafka-off) delivery of domain events to notifications — the default transport. It
 * mirrors {@link com.nexploy.campuslink.shared.event.DomainEventAuditListener}: each handler fires
 * only after the publishing transaction commits ({@link TransactionPhase#AFTER_COMMIT}) and runs in
 * its own new transaction, so a notification is never created for work that rolled back and a
 * failure here never rolls back the originating business operation. The actual event→notification
 * mapping lives in {@link NotificationEventHandlers}, which this listener simply delegates to.
 *
 * <p>Gated on {@code nexploy.kafka.enabled=false} (the default). When the Kafka transport is enabled
 * this bean stands down and {@link KafkaNotificationConsumer} drives the same handlers from the
 * topic instead, so a domain event produces a notification exactly once regardless of transport.
 */
@Component
@ConditionalOnProperty(name = "nexploy.kafka.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationEventHandlers handlers;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOfferExtended(OfferExtendedEvent event) {
        handlers.onOfferExtended(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOfferAccepted(OfferAcceptedEvent event) {
        handlers.onOfferAccepted(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOfferDeclined(OfferDeclinedEvent event) {
        handlers.onOfferDeclined(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOfferRevoked(OfferRevokedEvent event) {
        handlers.onOfferRevoked(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onShortlistDecided(ShortlistDecidedEvent event) {
        handlers.onShortlistDecided(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApplicationAdvanced(ApplicationAdvancedEvent event) {
        handlers.onApplicationAdvanced(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewScheduled(InterviewScheduledEvent event) {
        handlers.onInterviewScheduled(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewRescheduled(InterviewRescheduledEvent event) {
        handlers.onInterviewRescheduled(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewCancelled(InterviewCancelledEvent event) {
        handlers.onInterviewCancelled(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewResultRecorded(InterviewResultRecordedEvent event) {
        handlers.onInterviewResultRecorded(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDocumentReviewed(DocumentReviewedEvent event) {
        handlers.onDocumentReviewed(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterventionAssigned(InterventionAssignedEvent event) {
        handlers.onInterventionAssigned(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStudentRiskDetected(StudentRiskDetectedEvent event) {
        handlers.onStudentRiskDetected(event);
    }
}
