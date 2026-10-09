package com.nexploy.campuslink.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import com.nexploy.campuslink.schedule.dto.ScheduleConflictResponse;
import com.nexploy.campuslink.schedule.entity.BlockedSlot;
import com.nexploy.campuslink.schedule.entity.ConflictType;
import com.nexploy.campuslink.schedule.entity.EventAssignment;
import com.nexploy.campuslink.schedule.entity.PanelMember;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.repository.BlockedSlotRepository;
import com.nexploy.campuslink.schedule.repository.EventAssignmentRepository;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceTest {

    @Mock
    private ScheduleEventRepository scheduleEventRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private BlockedSlotRepository blockedSlotRepository;

    @Mock
    private PanelMemberRepository panelMemberRepository;

    @Mock
    private EventAssignmentRepository eventAssignmentRepository;

    @InjectMocks
    private ConflictDetectionService conflictDetectionService;

    @Test
    void eventsTouchingAtBoundaryDoNotConflict() {
        ScheduleEvent first = event("first", "10:00:00Z", "12:00:00Z");
        ScheduleEvent second = event("second", "12:00:00Z", "14:00:00Z");
        stubEvents(first, second);
        stubEmptyResources();

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void overlappingEventsReportVenueAndPanelConflicts() {
        ScheduleEvent first = event("first", "10:00:00Z", "12:00:00Z");
        ScheduleEvent second = event("second", "11:00:00Z", "13:00:00Z");
        stubEvents(first, second);
        stubEmptyResources();

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).extracting(ScheduleConflictResponse::type)
            .containsExactlyInAnyOrder(ConflictType.VENUE_OVERLAP, ConflictType.PANEL_OVERLAP);
    }

    @Test
    void venueCapacityFromTableTriggersConflict() {
        ScheduleEvent full = event("full", "10:00:00Z", "12:00:00Z");
        full.setExpectedParticipants(100);
        full.setVenueCapacity(200);
        stubEvents(full);
        when(venueRepository.findAll()).thenReturn(List.of(
            Venue.builder().id("lab-2").campusId("campus_demo").name("Lab 2").capacity(80).active(true).build()));
        when(blockedSlotRepository.findAll()).thenReturn(List.of());
        when(panelMemberRepository.findAll()).thenReturn(List.of());

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).extracting(ScheduleConflictResponse::type)
            .containsExactly(ConflictType.VENUE_CAPACITY);
    }

    @Test
    void blockedSlotOverlapIsReported() {
        ScheduleEvent event = event("evt", "10:00:00Z", "12:00:00Z");
        stubEvents(event);
        when(venueRepository.findAll()).thenReturn(List.of());
        when(blockedSlotRepository.findAll()).thenReturn(List.of(BlockedSlot.builder()
            .id("blk")
            .campusId("campus_demo")
            .venueId("lab-2")
            .reason("Examination")
            .startsAt(OffsetDateTime.parse("2026-09-22T11:00:00Z"))
            .endsAt(OffsetDateTime.parse("2026-09-22T13:00:00Z"))
            .build()));
        when(panelMemberRepository.findAll()).thenReturn(List.of());

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).extracting(ScheduleConflictResponse::type)
            .containsExactly(ConflictType.CAMPUS_BLOCK);
    }

    @Test
    void sharedPanelMemberAcrossDifferentPanelsIsReported() {
        ScheduleEvent first = event("first", "10:00:00Z", "12:00:00Z");
        first.setVenueId("lab-2");
        first.setPanelId("panel-a");
        ScheduleEvent second = event("second", "11:00:00Z", "13:00:00Z");
        second.setVenueId("lab-3");
        second.setPanelId("panel-b");
        stubEvents(first, second);
        when(venueRepository.findAll()).thenReturn(List.of());
        when(blockedSlotRepository.findAll()).thenReturn(List.of());
        when(panelMemberRepository.findAll()).thenReturn(List.of(
            PanelMember.builder().id("shared").panelId("panel-a").memberName("Asha").build(),
            PanelMember.builder().id("shared").panelId("panel-b").memberName("Asha").build()));

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).extracting(ScheduleConflictResponse::type)
            .containsExactly(ConflictType.PANEL_MEMBER_OVERLAP);
    }

    @Test
    void sharedStudentAcrossOverlappingEventsIsReported() {
        ScheduleEvent first = event("first", "10:00:00Z", "12:00:00Z");
        first.setVenueId("lab-2");
        first.setPanelId("panel-a");
        ScheduleEvent second = event("second", "11:00:00Z", "13:00:00Z");
        second.setVenueId("lab-3");
        second.setPanelId("panel-b");
        stubEvents(first, second);
        when(venueRepository.findAll()).thenReturn(List.of());
        when(blockedSlotRepository.findAll()).thenReturn(List.of());
        when(panelMemberRepository.findAll()).thenReturn(List.of());
        when(eventAssignmentRepository.findAll()).thenReturn(List.of(
            assignment("asg_1", "first", "stu_1"),
            assignment("asg_2", "second", "stu_1")));

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).extracting(ScheduleConflictResponse::type)
            .containsExactly(ConflictType.STUDENT_OVERLAP);
        assertThat(conflicts).extracting(ScheduleConflictResponse::resourceId).containsExactly("stu_1");
    }

    @Test
    void differentStudentsInOverlappingEventsDoNotConflict() {
        ScheduleEvent first = event("first", "10:00:00Z", "12:00:00Z");
        first.setVenueId("lab-2");
        first.setPanelId("panel-a");
        ScheduleEvent second = event("second", "11:00:00Z", "13:00:00Z");
        second.setVenueId("lab-3");
        second.setPanelId("panel-b");
        stubEvents(first, second);
        when(venueRepository.findAll()).thenReturn(List.of());
        when(blockedSlotRepository.findAll()).thenReturn(List.of());
        when(panelMemberRepository.findAll()).thenReturn(List.of());
        when(eventAssignmentRepository.findAll()).thenReturn(List.of(
            assignment("asg_1", "first", "stu_1"),
            assignment("asg_2", "second", "stu_2")));

        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(null);

        assertThat(conflicts).isEmpty();
    }

    private void stubEvents(ScheduleEvent... events) {
        when(scheduleEventRepository.findAll()).thenReturn(List.of(events));
    }

    private void stubEmptyResources() {
        when(venueRepository.findAll()).thenReturn(List.of());
        when(blockedSlotRepository.findAll()).thenReturn(List.of());
        when(panelMemberRepository.findAll()).thenReturn(List.of());
    }

    private ScheduleEvent event(String id, String startsAt, String endsAt) {
        return ScheduleEvent.builder()
            .id(id)
            .campusId("campus_demo")
            .venueId("lab-2")
            .panelId("panel-a")
            .startsAt(OffsetDateTime.parse("2026-09-22T" + startsAt))
            .endsAt(OffsetDateTime.parse("2026-09-22T" + endsAt))
            .expectedParticipants(10)
            .venueCapacity(20)
            .build();
    }

    private EventAssignment assignment(String id, String eventId, String studentId) {
        return EventAssignment.builder()
            .id(id)
            .eventId(eventId)
            .studentId(studentId)
            .campusId("campus_demo")
            .assignmentRole("CANDIDATE")
            .build();
    }
}
