package com.nexploy.campuslink.schedule.service;

import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.event.InterviewCancelledEvent;
import com.nexploy.campuslink.interview.event.InterviewRescheduledEvent;
import com.nexploy.campuslink.interview.event.InterviewScheduledEvent;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.schedule.entity.ActivityType;
import com.nexploy.campuslink.schedule.entity.EventAssignment;
import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.ParticipantMode;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.repository.EventAssignmentRepository;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges scheduled interviews onto the officer's Drive Orchestrator by mirroring each interview into a
 * {@code schedule_events} row. Interviews (recruiter/officer Interviews pages) and schedule events
 * (Drive Orchestrator + conflict detection) are otherwise decoupled tables with no data flow, so a
 * booked interview would never appear on the officer calendar without this listener.
 *
 * <p>Mirrors {@link com.nexploy.campuslink.notification.service.NotificationEventListener}: each handler
 * fires only after the interview transaction commits ({@link TransactionPhase#AFTER_COMMIT}) and runs in
 * its own new transaction, so a mirror-sync failure can never roll back or 500 the recruiter's
 * scheduling action, and no event is mirrored for work that rolled back.
 *
 * <p>The mirror event's id is derived deterministically from the interview id ({@code evt_<interviewId>})
 * so schedule/reschedule/cancel all address the same row without needing to write back
 * {@code interviews.schedule_event_id} — keeping the {@code interviews_event_fk} out of the picture and
 * making a redelivered event idempotent. Not gated on the Kafka toggle: no Kafka consumer writes
 * schedule events, and the deterministic id + upsert make a double-fire harmless.
 */
@Component
@RequiredArgsConstructor
public class InterviewScheduleBridge {

    private static final Logger log = LoggerFactory.getLogger(InterviewScheduleBridge.class);

    /** Interviews carry only a start time; a schedule event needs an end strictly after the start. */
    private static final int DEFAULT_SLOT_MINUTES = 60;
    /** One candidate per interview slot — keeps venue-capacity conflict detection meaningful. */
    private static final int PARTICIPANTS_PER_INTERVIEW = 1;

    private final InterviewRepository interviewRepository;
    private final ScheduleEventRepository scheduleEventRepository;
    private final VenueRepository venueRepository;
    private final PanelRepository panelRepository;
    private final EventAssignmentRepository eventAssignmentRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewScheduled(InterviewScheduledEvent event) {
        upsertMirror(event.interviewId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewRescheduled(InterviewRescheduledEvent event) {
        upsertMirror(event.interviewId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onInterviewCancelled(InterviewCancelledEvent event) {
        String eventId = mirrorId(event.interviewId());
        if (scheduleEventRepository.existsById(eventId)) {
            // Remove the candidate assignment first so the event_assignments FK never blocks the delete.
            eventAssignmentRepository.deleteByEventId(eventId);
            scheduleEventRepository.deleteById(eventId);
        }
    }

    /**
     * Creates or updates the mirror schedule event from the interview's current state. Re-reads the
     * interview (committed by the originating transaction) for its drive, time and venue/panel names.
     * A missing interview or a missing start time yields no mirror rather than a failure.
     */
    private void upsertMirror(String interviewId) {
        Interview interview = interviewRepository.findById(interviewId).orElse(null);
        if (interview == null || interview.getScheduledAt() == null) {
            return;
        }

        OffsetDateTime startsAt = interview.getScheduledAt();
        OffsetDateTime endsAt = startsAt.plusMinutes(DEFAULT_SLOT_MINUTES);
        Optional<Venue> venue = resolveVenue(interview.getCampusId(), interview.getVenue());
        String venueId = venue.map(Venue::getId).orElse(null);
        Integer venueCapacity = venue.map(Venue::getCapacity).orElse(null);
        String panelId = resolvePanelId(interview.getCampusId(), interview.getPanel());

        String mirrorId = mirrorId(interviewId);
        ScheduleEvent mirror = scheduleEventRepository.findById(mirrorId)
            .orElseGet(() -> ScheduleEvent.builder().id(mirrorId).conflict(false).build());

        mirror.setDriveId(interview.getDriveId());
        mirror.setCampusId(interview.getCampusId());
        mirror.setVenueId(venueId);
        mirror.setPanelId(panelId);
        mirror.setStartsAt(startsAt);
        mirror.setEndsAt(endsAt);
        mirror.setExpectedParticipants(PARTICIPANTS_PER_INTERVIEW);
        mirror.setVenueCapacity(venueCapacity);
        // An interview mirror is always a single-candidate INDIVIDUAL interview activity.
        mirror.setActivityType(ActivityType.INTERVIEW);
        mirror.setParticipantMode(ParticipantMode.INDIVIDUAL);
        mirror.setParticipantCount(PARTICIPANTS_PER_INTERVIEW);

        scheduleEventRepository.save(mirror);
        upsertCandidateAssignment(interview, mirrorId);
        log.debug("Mirrored interview {} to schedule event {} ({} - {})", interviewId, mirrorId, startsAt, endsAt);
    }

    /**
     * Links the interview's student to the mirror event in {@code event_assignments} so
     * {@link ConflictDetectionService} can detect a student double-booked across overlapping events.
     * Deterministic id ({@code asg_<interviewId>}) makes reschedule/redelivery idempotent. Skipped
     * when the interview carries no student (nothing to link).
     */
    private void upsertCandidateAssignment(Interview interview, String eventId) {
        if (interview.getStudentId() == null || interview.getStudentId().isBlank()) {
            return;
        }
        String assignmentId = "asg_" + interview.getId();
        EventAssignment assignment = eventAssignmentRepository.findById(assignmentId)
            .orElseGet(() -> EventAssignment.builder()
                .id(assignmentId).createdAt(OffsetDateTime.now()).build());
        assignment.setEventId(eventId);
        assignment.setStudentId(interview.getStudentId());
        assignment.setCampusId(interview.getCampusId());
        assignment.setAssignmentRole("CANDIDATE");
        eventAssignmentRepository.save(assignment);
    }

    private static String mirrorId(String interviewId) {
        return "evt_" + interviewId;
    }

    /** Resolves an interview's free-text venue name to a campus venue (case-insensitive); empty if none. */
    private Optional<Venue> resolveVenue(String campusId, String venueName) {
        if (venueName == null || venueName.isBlank()) {
            return Optional.empty();
        }
        return venueRepository.findByCampusId(campusId).stream()
            .filter(venue -> venueName.equalsIgnoreCase(venue.getName()))
            .findFirst();
    }

    /** Resolves an interview's free-text panel name to a campus panel id (case-insensitive); null if none. */
    private String resolvePanelId(String campusId, String panelName) {
        if (panelName == null || panelName.isBlank()) {
            return null;
        }
        return panelRepository.findByCampusId(campusId).stream()
            .filter(panel -> panelName.equalsIgnoreCase(panel.getName()))
            .map(Panel::getId)
            .findFirst()
            .orElse(null);
    }
}
