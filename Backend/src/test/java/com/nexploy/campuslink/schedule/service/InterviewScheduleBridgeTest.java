package com.nexploy.campuslink.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewScheduleBridgeTest {

    private final InterviewRepository interviewRepository = mock(InterviewRepository.class);
    private final ScheduleEventRepository scheduleEventRepository = mock(ScheduleEventRepository.class);
    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final PanelRepository panelRepository = mock(PanelRepository.class);
    private final EventAssignmentRepository eventAssignmentRepository = mock(EventAssignmentRepository.class);

    private final InterviewScheduleBridge bridge = new InterviewScheduleBridge(
        interviewRepository, scheduleEventRepository, venueRepository, panelRepository,
        eventAssignmentRepository);

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-09-28T09:00:00+05:30");

    @Test
    void scheduledCreatesMirrorEventWithResolvedVenueAndPanel() {
        Interview interview = interview("int_1", "cmp_main", "drv_9", START, "Interview Hall A", "Panel Alpha");
        when(interviewRepository.findById("int_1")).thenReturn(Optional.of(interview));
        when(scheduleEventRepository.findById("evt_int_1")).thenReturn(Optional.empty());
        when(venueRepository.findByCampusId("cmp_main"))
            .thenReturn(List.of(venue("venue_1", "cmp_main", "Interview Hall A", 30)));
        when(panelRepository.findByCampusId("cmp_main"))
            .thenReturn(List.of(panel("panel_1", "cmp_main", "Panel Alpha")));

        bridge.onInterviewScheduled(InterviewScheduledEvent.of(
            "cmp_main", "int_1", "stu_1", null, "TCS", START, OffsetDateTime.now()));

        ScheduleEvent saved = captureSaved();
        assertThat(saved.getId()).isEqualTo("evt_int_1");
        assertThat(saved.getDriveId()).isEqualTo("drv_9");
        assertThat(saved.getCampusId()).isEqualTo("cmp_main");
        assertThat(saved.getVenueId()).isEqualTo("venue_1");
        assertThat(saved.getPanelId()).isEqualTo("panel_1");
        assertThat(saved.getStartsAt()).isEqualTo(START);
        assertThat(saved.getEndsAt()).isEqualTo(START.plusMinutes(60));
        assertThat(saved.getExpectedParticipants()).isEqualTo(1);
        assertThat(saved.getVenueCapacity()).isEqualTo(30);
        assertThat(saved.isConflict()).isFalse();
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.INTERVIEW);
        assertThat(saved.getParticipantMode()).isEqualTo(ParticipantMode.INDIVIDUAL);
        assertThat(saved.getParticipantCount()).isEqualTo(1);
    }

    @Test
    void scheduledLinksStudentToMirrorViaEventAssignment() {
        Interview interview = Interview.builder()
            .id("int_a").campusId("cmp_main").driveId("drv_9").studentId("stu_7")
            .scheduledAt(START).venue("Unknown Room").panel("Ghost Panel").build();
        when(interviewRepository.findById("int_a")).thenReturn(Optional.of(interview));
        when(scheduleEventRepository.findById("evt_int_a")).thenReturn(Optional.empty());
        when(venueRepository.findByCampusId("cmp_main")).thenReturn(List.of());
        when(panelRepository.findByCampusId("cmp_main")).thenReturn(List.of());

        bridge.onInterviewScheduled(InterviewScheduledEvent.of(
            "cmp_main", "int_a", "stu_7", null, "TCS", START, OffsetDateTime.now()));

        ArgumentCaptor<EventAssignment> captor = ArgumentCaptor.forClass(EventAssignment.class);
        verify(eventAssignmentRepository).save(captor.capture());
        EventAssignment assignment = captor.getValue();
        assertThat(assignment.getId()).isEqualTo("asg_int_a");
        assertThat(assignment.getEventId()).isEqualTo("evt_int_a");
        assertThat(assignment.getStudentId()).isEqualTo("stu_7");
        assertThat(assignment.getCampusId()).isEqualTo("cmp_main");
        assertThat(assignment.getAssignmentRole()).isEqualTo("CANDIDATE");
    }

    @Test
    void unresolvedVenueAndPanelNamesLeaveIdsNull() {
        Interview interview = interview("int_2", "cmp_main", "drv_9", START, "Unknown Room", "Ghost Panel");
        when(interviewRepository.findById("int_2")).thenReturn(Optional.of(interview));
        when(scheduleEventRepository.findById("evt_int_2")).thenReturn(Optional.empty());
        when(venueRepository.findByCampusId("cmp_main")).thenReturn(List.of());
        when(panelRepository.findByCampusId("cmp_main")).thenReturn(List.of());

        bridge.onInterviewScheduled(InterviewScheduledEvent.of(
            "cmp_main", "int_2", "stu_1", null, "TCS", START, OffsetDateTime.now()));

        ScheduleEvent saved = captureSaved();
        assertThat(saved.getVenueId()).isNull();
        assertThat(saved.getPanelId()).isNull();
        assertThat(saved.getVenueCapacity()).isNull();
    }

    @Test
    void rescheduledUpdatesExistingMirrorInPlace() {
        Interview interview = interview("int_3", "cmp_main", "drv_9",
            START.plusHours(2), "Interview Hall A", null);
        when(interviewRepository.findById("int_3")).thenReturn(Optional.of(interview));
        ScheduleEvent existing = ScheduleEvent.builder().id("evt_int_3").conflict(false)
            .driveId("drv_9").campusId("cmp_main").startsAt(START).endsAt(START.plusMinutes(60)).build();
        when(scheduleEventRepository.findById("evt_int_3")).thenReturn(Optional.of(existing));
        when(venueRepository.findByCampusId("cmp_main"))
            .thenReturn(List.of(venue("venue_1", "cmp_main", "Interview Hall A", 30)));
        when(panelRepository.findByCampusId("cmp_main")).thenReturn(List.of());

        bridge.onInterviewRescheduled(InterviewRescheduledEvent.of(
            "cmp_main", "int_3", "stu_1", null, "TCS", START.plusHours(2), OffsetDateTime.now()));

        ScheduleEvent saved = captureSaved();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getStartsAt()).isEqualTo(START.plusHours(2));
        assertThat(saved.getEndsAt()).isEqualTo(START.plusHours(2).plusMinutes(60));
    }

    @Test
    void cancelledDeletesMirrorWhenPresent() {
        when(scheduleEventRepository.existsById("evt_int_4")).thenReturn(true);

        bridge.onInterviewCancelled(InterviewCancelledEvent.of(
            "cmp_main", "int_4", "stu_1", null, "TCS", OffsetDateTime.now()));

        verify(eventAssignmentRepository).deleteByEventId("evt_int_4");
        verify(scheduleEventRepository).deleteById("evt_int_4");
    }

    @Test
    void cancelledIsNoOpWhenMirrorAbsent() {
        when(scheduleEventRepository.existsById("evt_int_5")).thenReturn(false);

        bridge.onInterviewCancelled(InterviewCancelledEvent.of(
            "cmp_main", "int_5", "stu_1", null, "TCS", OffsetDateTime.now()));

        verify(scheduleEventRepository, never()).deleteById(any());
    }

    @Test
    void missingInterviewCreatesNoMirror() {
        when(interviewRepository.findById("int_gone")).thenReturn(Optional.empty());

        bridge.onInterviewScheduled(InterviewScheduledEvent.of(
            "cmp_main", "int_gone", "stu_1", null, "TCS", START, OffsetDateTime.now()));

        verify(scheduleEventRepository, never()).save(any());
    }

    private ScheduleEvent captureSaved() {
        ArgumentCaptor<ScheduleEvent> captor = ArgumentCaptor.forClass(ScheduleEvent.class);
        verify(scheduleEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Interview interview(String id, String campusId, String driveId,
                                       OffsetDateTime scheduledAt, String venue, String panel) {
        return Interview.builder()
            .id(id).campusId(campusId).driveId(driveId).scheduledAt(scheduledAt)
            .venue(venue).panel(panel).build();
    }

    private static Venue venue(String id, String campusId, String name, int capacity) {
        return Venue.builder().id(id).campusId(campusId).name(name).capacity(capacity).active(true).build();
    }

    private static Panel panel(String id, String campusId, String name) {
        return Panel.builder().id(id).campusId(campusId).name(name).active(true).build();
    }
}
