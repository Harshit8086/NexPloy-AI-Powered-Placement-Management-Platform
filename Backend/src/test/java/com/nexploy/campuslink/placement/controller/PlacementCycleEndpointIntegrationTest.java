package com.nexploy.campuslink.placement.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.placement.dto.ChangeCycleStatusRequest;
import com.nexploy.campuslink.placement.dto.CreatePlacementCycleRequest;
import com.nexploy.campuslink.placement.dto.UpdatePlacementCycleRequest;
import com.nexploy.campuslink.placement.entity.PlacementCycle;
import com.nexploy.campuslink.placement.repository.PlacementCycleRepository;
import java.time.LocalDate;
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
 * HTTP integration tests for the placement-cycle endpoints (GET list/one, POST create, PUT update,
 * PATCH status). Officers/admins own cycles; the status machine is PLANNING -> ACTIVE -> CLOSED
 * (+ reopen) -> ARCHIVED.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PlacementCycleEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlacementCycleRepository placementCycleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        placementCycleRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private PlacementCycle seed(String id, String campusId, String status) {
        return placementCycleRepository.save(PlacementCycle.builder()
            .id(id).campusId(campusId).name("Batch 2026").academicYear("2025-26")
            .startsOn(LocalDate.parse("2025-08-01")).endsOn(LocalDate.parse("2026-05-31"))
            .status(status).build());
    }

    @Test
    void officerCreatesCycle() throws Exception {
        CreatePlacementCycleRequest request = new CreatePlacementCycleRequest(
            "Batch 2027", "2026-27", "campus_a",
            LocalDate.parse("2026-08-01"), LocalDate.parse("2027-05-31"));

        mockMvc.perform(post("/api/placement-cycles")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Batch 2027"))
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.status").value("PLANNING"));
    }

    /** Officers are pinned to their assigned campus: a foreign campusId in the request body is ignored, not honored. */
    @Test
    void officerCreateIgnoresForeignCampusId() throws Exception {
        CreatePlacementCycleRequest request = new CreatePlacementCycleRequest(
            "Batch 2027", "2026-27", "campus_b",
            LocalDate.parse("2026-08-01"), LocalDate.parse("2027-05-31"));

        mockMvc.perform(post("/api/placement-cycles")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.campusId").value("campus_a"));
    }

    /** Admins range across campuses: an explicit campusId targets that campus. */
    @Test
    void adminCreatesCycleForRequestedCampus() throws Exception {
        CreatePlacementCycleRequest request = new CreatePlacementCycleRequest(
            "Batch 2027", "2026-27", "campus_b",
            LocalDate.parse("2026-08-01"), LocalDate.parse("2027-05-31"));

        mockMvc.perform(post("/api/placement-cycles")
                .with(authentication(as(UserRole.ADMIN, "campus_hq")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.campusId").value("campus_b"));
    }

    @Test
    void officerCannotReadAnotherCampusCycle() throws Exception {
        seed("cyc_1", "campus_b", "PLANNING");

        mockMvc.perform(get("/api/placement-cycles/cyc_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCannotUpdateAnotherCampusCycle() throws Exception {
        seed("cyc_1", "campus_b", "PLANNING");
        UpdatePlacementCycleRequest request = new UpdatePlacementCycleRequest(
            "Hijacked", "2025-26", null, null);

        mockMvc.perform(put("/api/placement-cycles/cyc_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void nonOfficerCannotCreateCycle() throws Exception {
        CreatePlacementCycleRequest request = new CreatePlacementCycleRequest(
            "Batch 2027", "2026-27", "campus_a", null, null);

        mockMvc.perform(post("/api/placement-cycles")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void getUnknownCycleIsNotFound() throws Exception {
        mockMvc.perform(get("/api/placement-cycles/missing")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void getCycleReturnsIt() throws Exception {
        seed("cyc_1", "campus_a", "PLANNING");

        mockMvc.perform(get("/api/placement-cycles/cyc_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("cyc_1"))
            .andExpect(jsonPath("$.status").value("PLANNING"));
    }

    @Test
    void statusTransitionPlanningToActive() throws Exception {
        seed("cyc_1", "campus_a", "PLANNING");

        mockMvc.perform(patch("/api/placement-cycles/cyc_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ChangeCycleStatusRequest("ACTIVE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void illegalStatusTransitionIsConflict() throws Exception {
        // PLANNING may only move to ACTIVE or ARCHIVED — PLANNING -> CLOSED is illegal.
        seed("cyc_1", "campus_a", "PLANNING");

        mockMvc.perform(patch("/api/placement-cycles/cyc_1/status")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ChangeCycleStatusRequest("CLOSED"))))
            .andExpect(status().isConflict());
    }

    @Test
    void officerUpdatesCycle() throws Exception {
        seed("cyc_1", "campus_a", "PLANNING");
        UpdatePlacementCycleRequest request = new UpdatePlacementCycleRequest(
            "Batch 2026 (revised)", "2025-26",
            LocalDate.parse("2025-09-01"), LocalDate.parse("2026-06-30"));

        mockMvc.perform(put("/api/placement-cycles/cyc_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Batch 2026 (revised)"));
    }
}
