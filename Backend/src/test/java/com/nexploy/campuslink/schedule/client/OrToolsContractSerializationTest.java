package com.nexploy.campuslink.schedule.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrToolsContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesWorkerRequestUsingSnakeCase() throws Exception {
        var request = new OrToolsOptimizationClient.OptimizationProblem(
            "version-1",
            List.of(new OrToolsOptimizationClient.OptimizationEvent(
                "event-1", 120, "lab-2", "panel-a", "2026-09-22T10:00:00Z",
                "2026-09-22T12:00:00Z", "slot-current", List.of("slot-current"),
                List.of("lab-2"), List.of("panel-a"), List.of("student-1"), 20
            )),
            List.of(new OrToolsOptimizationClient.TimeSlot(
                "slot-current", "2026-09-22T10:00:00Z", "2026-09-22T12:00:00Z"
            )),
            List.of(new OrToolsOptimizationClient.Venue("lab-2", 80)),
            List.of(new OrToolsOptimizationClient.Panel("panel-a", List.of("member-1"))),
            List.of(new OrToolsOptimizationClient.BlockedSlot(
                "2026-09-22T14:00:00Z", "2026-09-22T17:00:00Z", "lab-3"))
        );

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"schedule_version\"");
        assertThat(json).contains("\"candidate_slots\"");
        assertThat(json).contains("\"duration_minutes\"");
        assertThat(json).contains("\"allowed_venue_ids\"");
        assertThat(json).contains("\"blocked_slots\"");
        assertThat(json).contains("\"venue_id\"");
    }
}
