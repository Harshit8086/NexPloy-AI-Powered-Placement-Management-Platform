package com.nexploy.campuslink.schedule.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.BlockedSlot;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationAlternative;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationEvent;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationProblem;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.Panel;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.TimeSlot;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.Venue;
import com.nexploy.campuslink.schedule.dto.OptimizationResponse;
import com.nexploy.campuslink.schedule.dto.ScheduleConflictResponse;
import com.nexploy.campuslink.schedule.entity.OptimizationRun;
import com.nexploy.campuslink.schedule.entity.ScheduleConflictRecord;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.exception.InvalidOptimizationRunTransitionException;
import com.nexploy.campuslink.schedule.exception.OptimizationRunNotFoundException;
import com.nexploy.campuslink.schedule.exception.StaleOptimizationRunException;
import com.nexploy.campuslink.schedule.repository.BlockedSlotRepository;
import com.nexploy.campuslink.schedule.repository.OptimizationRunRepository;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleConflictRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleOptimizationService {

    private final ScheduleEventRepository scheduleEventRepository;
    private final OptimizationRunRepository optimizationRunRepository;
    private final VenueRepository venueRepository;
    private final PanelRepository panelRepository;
    private final PanelMemberRepository panelMemberRepository;
    private final BlockedSlotRepository blockedSlotRepository;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final OrToolsOptimizationClient optimizationClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public OptimizationResponse optimize(String campusId) {
        List<ScheduleEvent> events = campusId == null
            ? scheduleEventRepository.findAll()
            : scheduleEventRepository.findByCampusId(campusId);
        recordConflicts(events, campusId);
        String scheduleVersion = fingerprint(events);
        OptimizationProblem problem = buildProblem(events, scheduleVersion, campusId);
        var result = optimizationClient.optimize(problem);
        if (result == null || result.hardViolations() != 0
            || !("OPTIMAL".equals(result.status()) || "FEASIBLE".equals(result.status()))) {
            throw new IllegalStateException("OR-Tools did not return an approvable schedule: "
                + (result == null ? "empty response" : result.status()));
        }

        String runId = UUID.randomUUID().toString();
        OptimizationRun run = OptimizationRun.builder()
            .id(runId)
            .scheduleVersion(scheduleVersion)
            .status("PENDING")
            .createdAt(OffsetDateTime.now())
            .resultJson(write(result))
            .build();
        optimizationRunRepository.save(run);
        return toResponse(runId, scheduleVersion, result);
    }

    /** Reads a persisted optimization run and rebuilds its response from the stored result. */
    @Transactional(readOnly = true)
    public OptimizationResponse get(String runId) {
        OptimizationRun run = optimizationRunRepository.findById(runId)
            .orElseThrow(() -> new OptimizationRunNotFoundException(runId));
        return toResponse(run.getId(), run.getScheduleVersion(), read(run.getResultJson()));
    }

    /**
     * Rejects a pending run with a recorded reason, leaving the active schedule unchanged
     * (OR_TOOLS §6, §9). Only a PENDING run can be rejected; an already-approved or already-rejected
     * run is a conflicting transition.
     */
    @Transactional
    public OptimizationResponse reject(String runId, String reason) {
        OptimizationRun run = optimizationRunRepository.findById(runId)
            .orElseThrow(() -> new OptimizationRunNotFoundException(runId));
        if (!"PENDING".equals(run.getStatus())) {
            throw new InvalidOptimizationRunTransitionException(
                "Only a pending optimization run can be rejected; current status is " + run.getStatus());
        }
        run.setStatus("REJECTED");
        run.setRejectedAt(OffsetDateTime.now());
        run.setRejectedReason(reason);
        optimizationRunRepository.save(run);
        return toResponse(run.getId(), run.getScheduleVersion(), read(run.getResultJson()));
    }

    @Transactional
    public OptimizationResponse approve(String runId) {
        OptimizationRun run = optimizationRunRepository.findById(runId)
            .orElseThrow(() -> new OptimizationRunNotFoundException(runId));
        if (!"PENDING".equals(run.getStatus())) {
            throw new InvalidOptimizationRunTransitionException(
                "Only a pending optimization run can be approved; current status is " + run.getStatus());
        }
        List<ScheduleEvent> events = scheduleEventRepository.findAll();
        if (!run.getScheduleVersion().equals(fingerprint(events))) {
            throw new StaleOptimizationRunException();
        }
        var result = read(run.getResultJson());
        if (result.hardViolations() != 0 || result.selectedAssignments() == null) {
            throw new IllegalStateException("Optimization run has no valid assignments");
        }
        Map<String, ScheduleEvent> eventsById = events.stream()
            .collect(Collectors.toMap(ScheduleEvent::getId, Function.identity()));
        for (OptimizationAlternative assignment : result.selectedAssignments()) {
            ScheduleEvent event = eventsById.get(assignment.eventId());
            if (event == null) {
                throw new IllegalStateException("Worker returned unknown event: " + assignment.eventId());
            }
            event.setStartsAt(OffsetDateTime.parse(assignment.startsAt()));
            event.setEndsAt(OffsetDateTime.parse(assignment.endsAt()));
            event.setVenueId(assignment.venueId());
            event.setPanelId(assignment.panelId());
            event.setConflict(false);
        }
        scheduleEventRepository.saveAll(events);
        run.setStatus("APPROVED");
        run.setApprovedAt(OffsetDateTime.now());
        optimizationRunRepository.save(run);
        return toResponse(runId, run.getScheduleVersion(), result);
    }

    private void recordConflicts(List<ScheduleEvent> events, String campusId) {
        List<ScheduleConflictResponse> conflicts = conflictDetectionService.detect(campusId);
        Map<String, String> campusByEvent = events.stream()
            .collect(Collectors.toMap(ScheduleEvent::getId, ScheduleEvent::getCampusId, (left, right) -> left));
        if (campusId != null) {
            scheduleConflictRepository.deleteByCampusId(campusId);
        } else {
            events.stream().map(ScheduleEvent::getCampusId).distinct()
                .forEach(scheduleConflictRepository::deleteByCampusId);
        }
        OffsetDateTime detectedAt = OffsetDateTime.now();
        List<ScheduleConflictRecord> records = conflicts.stream().map(conflict -> ScheduleConflictRecord.builder()
            .id(UUID.randomUUID().toString())
            .campusId(campusByEvent.getOrDefault(conflict.eventId(), campusId))
            .conflictType(conflict.type())
            .eventId(conflict.eventId())
            .conflictingEventId(conflict.conflictingEventId())
            .resourceId(conflict.resourceId())
            .overlapStart(conflict.overlapStart())
            .overlapEnd(conflict.overlapEnd())
            .message(conflict.message())
            .detectedAt(detectedAt)
            .build()).toList();
        scheduleConflictRepository.saveAll(records);
    }

    private OptimizationProblem buildProblem(List<ScheduleEvent> events, String scheduleVersion, String campusId) {
        List<com.nexploy.campuslink.schedule.entity.Venue> venueEntities = campusId == null
            ? venueRepository.findAll()
            : venueRepository.findByCampusId(campusId);
        List<com.nexploy.campuslink.schedule.entity.Panel> panelEntities = campusId == null
            ? panelRepository.findAll()
            : panelRepository.findByCampusId(campusId);
        List<com.nexploy.campuslink.schedule.entity.BlockedSlot> blockedSlots = campusId == null
            ? blockedSlotRepository.findAll()
            : blockedSlotRepository.findByCampusId(campusId);

        List<Venue> venues = venueEntities.isEmpty()
            ? fallbackVenues(events)
            : venueEntities.stream()
                .map(venue -> new Venue(venue.getId(), venue.getCapacity() == null ? 1 : venue.getCapacity()))
                .toList();
        List<Panel> panels = panelEntities.isEmpty()
            ? fallbackPanels(events)
            : panelEntities.stream()
                .map(panel -> new Panel(panel.getId(), panelMemberRepository.findByPanelId(panel.getId())
                    .stream().map(com.nexploy.campuslink.schedule.entity.PanelMember::getId).toList()))
                .toList();

        List<String> venueIds = venues.stream().map(Venue::id).toList();
        List<String> panelIds = panels.stream().map(Panel::id).toList();

        List<TimeSlot> slots = new ArrayList<>();
        List<OptimizationEvent> optimizationEvents = new ArrayList<>();
        for (ScheduleEvent event : events) {
            String currentSlotId = event.getId() + "_current";
            slots.add(new TimeSlot(currentSlotId, event.getStartsAt().toString(), event.getEndsAt().toString()));

            // Always offer the alternative slot. The worker enforces per-venue blocked windows,
            // so it can move a blocked event to a different, non-blocked venue at the same time.
            OffsetDateTime alternativeStart = event.getStartsAt().plusHours(2);
            OffsetDateTime alternativeEnd = alternativeStart.plusMinutes(durationMinutes(event));
            String alternativeSlotId = event.getId() + "_alternative";
            slots.add(new TimeSlot(alternativeSlotId, alternativeStart.toString(), alternativeEnd.toString()));

            optimizationEvents.add(new OptimizationEvent(
                event.getId(), durationMinutes(event), event.getVenueId(), event.getPanelId(),
                event.getStartsAt().toString(), event.getEndsAt().toString(), currentSlotId,
                List.of(currentSlotId, alternativeSlotId), venueIds, panelIds,
                List.of(), event.getExpectedParticipants() == null ? 0 : event.getExpectedParticipants()
            ));
        }
        List<BlockedSlot> clientBlockedSlots = blockedSlots.stream()
            .map(slot -> new BlockedSlot(slot.getStartsAt().toString(), slot.getEndsAt().toString(), slot.getVenueId()))
            .toList();
        return new OptimizationProblem(scheduleVersion, optimizationEvents, slots, venues, panels, clientBlockedSlots);
    }

    private List<Venue> fallbackVenues(List<ScheduleEvent> events) {
        return events.stream()
            .filter(event -> event.getVenueId() != null)
            .collect(Collectors.toMap(ScheduleEvent::getVenueId, event -> new Venue(
                event.getVenueId(), event.getVenueCapacity() == null ? 1 : event.getVenueCapacity()),
                (left, right) -> left))
            .values().stream().toList();
    }

    private List<Panel> fallbackPanels(List<ScheduleEvent> events) {
        return events.stream()
            .filter(event -> event.getPanelId() != null)
            .collect(Collectors.toMap(ScheduleEvent::getPanelId,
                event -> new Panel(event.getPanelId(), List.of(event.getPanelId())), (left, right) -> left))
            .values().stream().toList();
    }

    private int durationMinutes(ScheduleEvent event) {
        return (int) (event.getEndsAt().toEpochSecond() - event.getStartsAt().toEpochSecond()) / 60;
    }

    private String fingerprint(List<ScheduleEvent> events) {
        String value = events.stream().sorted(Comparator.comparing(ScheduleEvent::getId))
            .map(event -> String.join("|", event.getId(), event.getStartsAt().toString(),
                event.getEndsAt().toString(), event.getVenueId(), event.getPanelId()))
            .collect(Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store optimization result", exception);
        }
    }

    private OrToolsOptimizationClient.OptimizationResult read(String value) {
        try {
            return objectMapper.readValue(value, OrToolsOptimizationClient.OptimizationResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read optimization result", exception);
        }
    }

    private OptimizationResponse toResponse(String runId, String scheduleVersion,
                                            OrToolsOptimizationClient.OptimizationResult result) {
        return new OptimizationResponse(runId, result.status(), scheduleVersion, result.hardViolations(),
            result.softScore(), result.alternatives(), result.selectedAssignments());
    }
}
