package com.nexploy.campuslink.schedule.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationResult;
import com.nexploy.campuslink.schedule.entity.ConflictType;
import com.nexploy.campuslink.schedule.entity.OptimizationRun;
import com.nexploy.campuslink.schedule.entity.ScheduleConflictRecord;
import com.nexploy.campuslink.schedule.repository.OptimizationRunRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleConflictRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the optimization-run read/reject and conflict-scoped
 * optimize endpoints (OR_TOOLS §6). Runs through real routing and the security filter chain
 * against in-memory H2 (the "test" profile). The GET and reject paths only read persisted runs,
 * so no ML worker is needed; the conflict-optimize error paths (unknown conflict -> 404, foreign
 * campus -> 403) short-circuit before the OR-Tools client is called.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OptimizationEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OptimizationRunRepository optimizationRunRepository;

    @Autowired
    private ScheduleConflictRepository scheduleConflictRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        optimizationRunRepository.deleteAll();
        scheduleConflictRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private OptimizationRun seedRun(String id, String status) throws Exception {
        OptimizationResult result = new OptimizationResult("FEASIBLE", 0, 7, List.of(), List.of());
        return optimizationRunRepository.save(OptimizationRun.builder()
            .id(id)
            .scheduleVersion("v-" + id)
            .status(status)
            .createdAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"))
            .resultJson(objectMapper.writeValueAsString(result))
            .build());
    }

    private ScheduleConflictRecord seedConflict(String id, String campusId) {
        return scheduleConflictRepository.save(ScheduleConflictRecord.builder()
            .id(id).campusId(campusId).conflictType(ConflictType.VENUE_OVERLAP)
            .eventId("evt_1").conflictingEventId("evt_2").resourceId("venue_1")
            .overlapStart(OffsetDateTime.parse("2026-01-01T10:00:00Z"))
            .overlapEnd(OffsetDateTime.parse("2026-01-01T11:00:00Z"))
            .message("Venue double-booked").detectedAt(OffsetDateTime.parse("2026-01-01T09:00:00Z"))
            .build());
    }

    @Test
    void getReturnsPersistedRun() throws Exception {
        seedRun("run_1", "PENDING");

        mockMvc.perform(get("/api/schedule/optimization-runs/run_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run_1"))
            .andExpect(jsonPath("$.scheduleVersion").value("v-run_1"))
            .andExpect(jsonPath("$.status").value("FEASIBLE"));
    }

    @Test
    void getUnknownRunReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/schedule/optimization-runs/missing")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void officerCanRejectPendingRun() throws Exception {
        seedRun("run_1", "PENDING");

        mockMvc.perform(post("/api/schedule/optimization-runs/run_1/reject")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"reason\":\"Clashes with convocation week\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.runId").value("run_1"));

        OptimizationRun rejected = optimizationRunRepository.findById("run_1").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(rejected.getRejectedReason())
            .isEqualTo("Clashes with convocation week");
        org.assertj.core.api.Assertions.assertThat(rejected.getRejectedAt()).isNotNull();
    }

    @Test
    void rejectingAnAlreadyRejectedRunIsConflict() throws Exception {
        seedRun("run_1", "REJECTED");

        mockMvc.perform(post("/api/schedule/optimization-runs/run_1/reject")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"reason\":\"Changed my mind\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void recruiterCannotRejectRun() throws Exception {
        seedRun("run_1", "PENDING");

        mockMvc.perform(post("/api/schedule/optimization-runs/run_1/reject")
                .with(authentication(as(UserRole.RECRUITER, "campus_a")))
                .contentType("application/json")
                .content("{\"reason\":\"No\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectWithBlankReasonIsBadRequest() throws Exception {
        seedRun("run_1", "PENDING");

        mockMvc.perform(post("/api/schedule/optimization-runs/run_1/reject")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content("{\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void optimizingUnknownConflictReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/schedule/conflicts/missing/optimize")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void recruiterCannotOptimizeConflictInAnotherCampus() throws Exception {
        seedConflict("conflict_1", "campus_a");

        mockMvc.perform(post("/api/schedule/conflicts/conflict_1/optimize")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotTriggerOptimization() throws Exception {
        mockMvc.perform(post("/api/schedule/optimize")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotOptimizeOwnCampusConflict() throws Exception {
        seedConflict("conflict_1", "campus_a");

        mockMvc.perform(post("/api/schedule/conflicts/conflict_1/optimize")
                .with(authentication(as(UserRole.STUDENT, "campus_a"))))
            .andExpect(status().isForbidden());
    }
}
