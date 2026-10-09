package com.nexploy.campuslink.schedule.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

public interface OrToolsOptimizationClient {

    OptimizationResult optimize(OptimizationProblem problem);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OptimizationProblem(
        String scheduleVersion,
        List<OptimizationEvent> events,
        List<TimeSlot> candidateSlots,
        List<Venue> venues,
        List<Panel> panels,
        List<BlockedSlot> blockedSlots
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BlockedSlot(String startsAt, String endsAt, String venueId) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OptimizationEvent(
        String eventId,
        int durationMinutes,
        String venueId,
        String panelId,
        String startsAt,
        String endsAt,
        String currentSlotId,
        List<String> candidateSlotIds,
        List<String> allowedVenueIds,
        List<String> allowedPanelIds,
        List<String> studentIds,
        int expectedParticipants
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record TimeSlot(String id, String startsAt, String endsAt) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Venue(String id, int capacity) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Panel(String id, List<String> memberIds) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OptimizationResult(
        String status,
        int hardViolations,
        int softScore,
        List<OptimizationAlternative> alternatives,
        List<OptimizationAlternative> selectedAssignments
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record OptimizationAlternative(
        int rank,
        String eventId,
        String startsAt,
        String endsAt,
        String venueId,
        String panelId,
        int hardViolations,
        int softScore,
        List<String> reasonCodes,
        List<String> evidence
    ) {
    }
}

