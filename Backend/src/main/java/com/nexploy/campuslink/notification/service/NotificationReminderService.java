package com.nexploy.campuslink.notification.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.repository.UserAccountRepository;
import com.nexploy.campuslink.document.entity.Document;
import com.nexploy.campuslink.document.entity.ReviewStatus;
import com.nexploy.campuslink.document.repository.DocumentRepository;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.notification.entity.NotificationType;
import com.nexploy.campuslink.notification.template.NotificationTemplate;
import com.nexploy.campuslink.notification.template.RenderedNotification;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Time-based notification reminders (Notification-system Step 8). Two scheduled sweeps turn
 * approaching deadlines into in-app notifications through the same
 * template → {@link NotificationService#createFromEvent} pipeline the event listener uses — so
 * reminders honour the recipient's per-type opt-out and are de-duplicated the same way.
 *
 * <p><b>At-most-once:</b> each reminder uses a stable {@code sourceEventId} derived from the entity
 * id ({@code interview-reminder:<id>} / {@code offer-deadline:<id>}), so the sweep can run as often
 * as it likes yet a given interview or offer is reminded exactly once — the idempotency guard in
 * {@code createFromEvent} drops repeats. No new state table is needed to track "already reminded".
 *
 * <p>The sweeps run on cron (top of each hour by default) and are safe to run on every instance.
 */
@Service
@RequiredArgsConstructor
public class NotificationReminderService {

    /** How far ahead an interview must be to earn a reminder. */
    private static final int INTERVIEW_LOOKAHEAD_HOURS = 24;

    /** How close an interview must be to earn the second, more urgent "starting soon" reminder. */
    private static final int INTERVIEW_IMMINENT_MINUTES = 60;

    /** A PENDING document older than this earns a reminder (staleness threshold, not a deadline). */
    private static final int DOCUMENT_PENDING_HOURS = 24;

    /** An EXTENDED offer older than this earns a recruiter follow-up nudge. */
    private static final int OFFER_FOLLOWUP_HOURS = 72;

    private final InterviewRepository interviewRepository;
    private final OfferRepository offerRepository;
    private final StudentRepository studentRepository;
    private final DocumentRepository documentRepository;
    private final UserAccountRepository userAccountRepository;
    private final RecruiterRepository recruiterRepository;
    private final NotificationService notificationService;

    /**
     * Reminds students of interviews starting within the next {@value #INTERVIEW_LOOKAHEAD_HOURS}
     * hours. Only SCHEDULED interviews qualify (cancelled/completed ones are skipped by the query).
     */
    @Scheduled(cron = "${nexploy.notifications.interview-reminder-cron:0 0 * * * *}")
    public void remindUpcomingInterviews() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime horizon = now.plusHours(INTERVIEW_LOOKAHEAD_HOURS);
        for (Interview interview : interviewRepository
                .findByStatusAndScheduledAtBetween(InterviewStatus.SCHEDULED, now, horizon)) {
            studentUsername(interview.getStudentId()).ifPresent(recipient ->
                dispatch("interview-reminder:" + interview.getId(),
                    interview.getCampusId(), recipient,
                    NotificationType.INTERVIEW_REMINDER, interview.getId(),
                    Map.of("round", roundClause(interview.getRound()),
                        "company", companyClause(interview.getCompanyName()),
                        "time", timeClause(interview.getScheduledAt()))));
        }
    }

    /**
     * Second, closer reminder: nudges students whose interview begins within the next
     * {@value #INTERVIEW_IMMINENT_MINUTES} minutes. Runs alongside {@link #remindUpcomingInterviews()}
     * with a distinct {@code interview-reminder-soon:<id>} source id, so a student gets both the
     * day-ahead heads-up and this imminent nudge — each one-shot per interview.
     */
    @Scheduled(cron = "${nexploy.notifications.interview-imminent-reminder-cron:0 0/15 * * * *}")
    public void remindImminentInterviews() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime horizon = now.plusMinutes(INTERVIEW_IMMINENT_MINUTES);
        for (Interview interview : interviewRepository
                .findByStatusAndScheduledAtBetween(InterviewStatus.SCHEDULED, now, horizon)) {
            studentUsername(interview.getStudentId()).ifPresent(recipient ->
                dispatch("interview-reminder-soon:" + interview.getId(),
                    interview.getCampusId(), recipient,
                    NotificationType.INTERVIEW_STARTING_SOON, interview.getId(),
                    Map.of("round", roundClause(interview.getRound()),
                        "company", companyClause(interview.getCompanyName()),
                        "time", timeClause(interview.getScheduledAt()))));
        }
    }

    /**
     * Reminds students whose extended offer is awaiting a response and whose acceptance deadline
     * falls today or tomorrow. Accepted/declined/revoked offers are skipped by the query.
     */
    @Scheduled(cron = "${nexploy.notifications.offer-deadline-reminder-cron:0 0 * * * *}")
    public void remindOfferDeadlines() {
        LocalDate today = LocalDate.now();
        for (Offer offer : offerRepository
                .findByStatusAndAcceptanceDeadlineBetween(OfferStatus.EXTENDED, today, today.plusDays(1))) {
            studentUsername(offer.getStudentId()).ifPresent(recipient ->
                dispatch("offer-deadline:" + offer.getId(),
                    offer.getCampusId(), recipient,
                    NotificationType.OFFER_DEADLINE, offer.getId(),
                    Map.of("company", companyClause(offer.getCompanyName()),
                        "deadline", String.valueOf(offer.getAcceptanceDeadline()))));
        }
    }

    /**
     * Reminds about documents stuck awaiting review for more than {@value #DOCUMENT_PENDING_HOURS}
     * hours. Two audiences: the owning <b>student</b> gets a per-document nudge; each campus's
     * <b>officers</b> get one daily digest of how many documents await their review. {@code uploadedAt}
     * is only a staleness threshold here — documents carry no due date.
     */
    @Scheduled(cron = "${nexploy.notifications.document-reminder-cron:0 0 * * * *}")
    public void remindPendingDocuments() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(DOCUMENT_PENDING_HOURS);
        Map<String, Integer> pendingByCampus = new HashMap<>();
        for (Document document : documentRepository
                .findByReviewStatusAndUploadedAtBefore(ReviewStatus.PENDING, cutoff)) {
            // Student: one reminder per stale document (idempotent on the document id).
            studentUsername(document.getStudentId()).ifPresent(recipient ->
                dispatch("document-pending:" + document.getId(),
                    document.getCampusId(), recipient,
                    NotificationType.DOCUMENT_REMINDER, document.getId(),
                    Map.of("type", documentTypeLabel(document),
                        "hours", String.valueOf(DOCUMENT_PENDING_HOURS))));
            if (document.getCampusId() != null) {
                pendingByCampus.merge(document.getCampusId(), 1, Integer::sum);
            }
        }
        // Officer: one campus-level digest per day (day bucket in the source id keeps it daily, not once-ever).
        long dayBucket = LocalDate.now().toEpochDay();
        for (Map.Entry<String, Integer> entry : pendingByCampus.entrySet()) {
            String campusId = entry.getKey();
            String sourceEventId = "document-review-pending:" + campusId + ":" + dayBucket;
            for (UserAccount officer : userAccountRepository
                    .findByRoleAndCampusIdAndEnabledTrue(UserRole.OFFICER, campusId)) {
                dispatch(sourceEventId, campusId, officer.getUsername(),
                    NotificationType.DOCUMENT_REVIEW_PENDING, campusId,
                    Map.of("count", String.valueOf(entry.getValue())));
            }
        }
    }

    /**
     * Nudges recruiters about offers still EXTENDED (no candidate response) more than
     * {@value #OFFER_FOLLOWUP_HOURS} hours after they were extended. One-shot per offer: the stable
     * {@code offer-followup:<id>} source id fires exactly once. An offer that leaves EXTENDED drops
     * out of the query automatically.
     */
    @Scheduled(cron = "${nexploy.notifications.offer-followup-reminder-cron:0 0 * * * *}")
    public void remindRecruitersOfPendingOffers() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(OFFER_FOLLOWUP_HOURS);
        for (Offer offer : offerRepository
                .findByStatusAndExtendedAtBefore(OfferStatus.EXTENDED, cutoff)) {
            recruiterUsername(offer.getRecruiterId()).ifPresent(recipient ->
                dispatch("offer-followup:" + offer.getId(),
                    offer.getCampusId(), recipient,
                    NotificationType.OFFER_FOLLOWUP, offer.getId(),
                    Map.of("company", companyClause(offer.getCompanyName()),
                        "offerId", offer.getId())));
        }
    }

    private void dispatch(String sourceEventId, String campusId, String recipient,
                          NotificationType type, String referenceId, Map<String, String> variables) {
        RenderedNotification message = NotificationTemplate.forType(type).render(variables);
        notificationService.createFromEvent(
            sourceEventId, campusId, recipient, type, referenceId, message.subject(), message.body());
    }

    /**
     * Resolves a student to their notification recipient — their login username (== email) — but only
     * when a matching {@code user_accounts} row exists. {@code notifications.recipient_username} is FK'd
     * to {@code user_accounts(username)}, so a data-only student profile with no login account (e.g. a
     * demo/seed row) must be skipped: feeding it to {@link NotificationService#createFromEvent} would
     * violate the FK and abort the whole scheduled sweep. The exact-match {@code existsById} mirrors the
     * FK's key, so a resolved recipient is always one the constraint will accept. A student with no
     * email maps to {@code empty} before the check.
     */
    private java.util.Optional<String> studentUsername(String studentId) {
        return studentRepository.findById(studentId)
            .map(Student::getEmail)
            .filter(userAccountRepository::existsById);
    }

    /** As {@link #studentUsername(String)}, for a recruiter recipient (same FK precondition). */
    private java.util.Optional<String> recruiterUsername(String recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .map(Recruiter::getEmail)
            .filter(userAccountRepository::existsById);
    }

    /** "Marksheet" / "Id proof" — a readable label for the document type, "" when absent. */
    private static String documentTypeLabel(Document document) {
        if (document.getType() == null) {
            return "";
        }
        String raw = document.getType().name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** " with Acme" when a company is known, else "". */
    private static String companyClause(String companyName) {
        return (companyName == null || companyName.isBlank()) ? "" : " with " + companyName;
    }

    /** " Technical Round 1" when a round is named, else "" — unnamed rounds read as before. */
    private static String roundClause(String round) {
        return (round == null || round.isBlank()) ? "" : " " + round;
    }

    /** " on 2026-01-01T10:00Z" when a time is known, else "". */
    private static String timeClause(OffsetDateTime scheduledAt) {
        return scheduledAt == null ? "" : " on " + scheduledAt;
    }
}
