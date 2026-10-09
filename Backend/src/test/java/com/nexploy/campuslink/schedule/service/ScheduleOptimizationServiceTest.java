package com.nexploy.campuslink.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationProblem;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationResult;
import com.nexploy.campuslink.schedule.entity.BlockedSlot;
import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.PanelMember;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.repository.BlockedSlotRepository;
import com.nexploy.campuslink.schedule.repository.OptimizationRunRepository;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleConflictRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleOptimizationServiceTest {

    @Mock
    private ScheduleEventRepository scheduleEventRepository;
    @Mock
    private OptimizationRunRepository optimizationRunRepository;
    @Mock
    private VenueRepository venueRepository;
    @Mock
    private PanelRepository panelRepository;
    @Mock
    private PanelMemberRepository panelMemberRepository;
    @Mock
    private BlockedSlotRepository blockedSlotRepository;
    @Mock
    private ScheduleConflictRepository scheduleConflictRepository;
    @Mock
    private ConflictDetectionService conflictDetectionService;
    @Mock
    private OrToolsOptimizationClient optimizationClient;

    @Test
    void buildsProblemFromResourceTablesAndExcludesBlockedAlternativeSlot() {
        ScheduleEvent event = ScheduleEvent.builder()
            .id("evt")
            .campusId("campus_demo")
            .venueId("lab-2")
            .panelId("panel-a")
            .startsAt(OffsetDateTime.parse("2026-09-22T10:00:00Z"))
            .endsAt(OffsetDateTime.parse("2026-09-22T12:00:00Z"))
            .expectedParticipants(40)
            .venueCapacity(20)
            .build();
        when(scheduleEventRepository.findByCampusId("campus_demo")).thenReturn(List.of(event));
        when(venueRepository.findByCampusId("campus_demo")).thenReturn(List.of(
            Venue.builder().id("lab-2").campusId("campus_demo").name("Lab 2").capacity(80).active(true).build()));
        when(panelRepository.findByCampusId("campus_demo")).thenReturn(List.of(
            Panel.builder().id("panel-a").campusId("campus_demo").name("Panel A").active(true).build()));
        when(panelMemberRepository.findByPanelId("panel-a")).thenReturn(List.of(
            PanelMember.builder().id("pm1").panelId("panel-a").memberName("Asha").build()));
        when(blockedSlotRepository.findByCampusId("campus_demo")).thenReturn(List.of(
            BlockedSlot.builder().id("blk").campusId("campus_demo").venueId("lab-2").reason("Exam")
                .startsAt(OffsetDateTime.parse("2026-09-22T12:00:00Z"))
                .endsAt(OffsetDateTime.parse("2026-09-22T14:00:00Z"))
                .build()));
        when(conflictDetectionService.detect("campus_demo")).thenReturn(List.of());
        when(optimizationClient.optimize(any())).thenReturn(
            new OptimizationResult("FEASIBLE", 0, 5, List.of(), List.of()));

        ScheduleOptimizationService service = new ScheduleOptimizationService(
            scheduleEventRepository, optimizationRunRepository, venueRepository, panelRepository,
            panelMemberRepository, blockedSlotRepository, scheduleConflictRepository, conflictDetectionService,
            optimizationClient, new ObjectMapper());

        service.optimize("campus_demo");

        ArgumentCaptor<OptimizationProblem> captor = ArgumentCaptor.forClass(OptimizationProblem.class);
        org.mockito.Mockito.verify(optimizationClient).optimize(captor.capture());
        OptimizationProblem problem = captor.getValue();

        assertThat(problem.venues()).extracting(OrToolsOptimizationClient.Venue::id,
            OrToolsOptimizationClient.Venue::capacity).containsExactly(org.assertj.core.groups.Tuple.tuple("lab-2", 80));
        assertThat(problem.panels()).hasSize(1);
        assertThat(problem.panels().get(0).memberIds()).containsExactly("pm1");
        assertThat(problem.events().get(0).candidateSlotIds()).containsExactly("evt_current", "evt_alternative");
        assertThat(problem.candidateSlots()).extracting(OrToolsOptimizationClient.TimeSlot::id)
            .containsExactly("evt_current", "evt_alternative");
        assertThat(problem.blockedSlots()).extracting(OrToolsOptimizationClient.BlockedSlot::venueId)
            .containsExactly("lab-2");
    }
}
