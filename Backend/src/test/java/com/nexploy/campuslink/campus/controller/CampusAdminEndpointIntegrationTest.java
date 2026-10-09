package com.nexploy.campuslink.campus.controller;

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
import com.nexploy.campuslink.campus.dto.CreateCampusRequest;
import com.nexploy.campuslink.campus.dto.SetCampusActiveRequest;
import com.nexploy.campuslink.campus.dto.UpdateCampusRequest;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.repository.CampusRepository;
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
 * HTTP integration tests for campus administration (POST create, PUT update, PATCH active).
 * Campuses are the tenant boundary, so their lifecycle is ADMIN-only; officers and students are
 * forbidden.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CampusAdminEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        campusRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Campus seed(String id) {
        return campusRepository.save(Campus.builder()
            .id(id).name("Old Name").city("Metropolis").state("State").active(true).build());
    }

    @Test
    void adminCreatesCampus() throws Exception {
        CreateCampusRequest request = new CreateCampusRequest(
            "campus_new", "New Campus", "Pune", "MH");

        mockMvc.perform(post("/api/campuses")
                .with(authentication(as(UserRole.ADMIN, "campus_hq")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("campus_new"))
            .andExpect(jsonPath("$.name").value("New Campus"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void officerCannotCreateCampus() throws Exception {
        CreateCampusRequest request = new CreateCampusRequest(
            "campus_new", "New Campus", "Pune", "MH");

        mockMvc.perform(post("/api/campuses")
                .with(authentication(as(UserRole.OFFICER, "campus_hq")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatesCampus() throws Exception {
        seed("campus_a");
        UpdateCampusRequest request = new UpdateCampusRequest("Renamed Campus", "Nagpur", "MH");

        mockMvc.perform(put("/api/campuses/campus_a")
                .with(authentication(as(UserRole.ADMIN, "campus_hq")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed Campus"))
            .andExpect(jsonPath("$.city").value("Nagpur"));
    }

    @Test
    void adminDeactivatesCampus() throws Exception {
        seed("campus_a");

        mockMvc.perform(patch("/api/campuses/campus_a/active")
                .with(authentication(as(UserRole.ADMIN, "campus_hq")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SetCampusActiveRequest(false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void studentCannotDeactivateCampus() throws Exception {
        seed("campus_a");

        mockMvc.perform(patch("/api/campuses/campus_a/active")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SetCampusActiveRequest(false))))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateUnknownCampusIsNotFound() throws Exception {
        UpdateCampusRequest request = new UpdateCampusRequest("Renamed", "City", "State");

        mockMvc.perform(put("/api/campuses/missing")
                .with(authentication(as(UserRole.ADMIN, "campus_hq")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @Test
    void officerGetsCampusById() throws Exception {
        seed("campus_a");

        mockMvc.perform(get("/api/campuses/campus_a")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("campus_a"))
            .andExpect(jsonPath("$.name").value("Old Name"));
    }

    /** A missing campus is a 404 (CampusNotFoundException), which the frontend's getCampus maps to a graceful undefined. */
    @Test
    void getUnknownCampusIsNotFound() throws Exception {
        mockMvc.perform(get("/api/campuses/missing")
                .with(authentication(as(UserRole.ADMIN, "campus_hq"))))
            .andExpect(status().isNotFound());
    }

    /** Officers are campus-scoped: reading any campus other than their own is denied outright. */
    @Test
    void officerCannotGetAnotherCampusById() throws Exception {
        seed("campus_b");

        mockMvc.perform(get("/api/campuses/campus_b")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerListsOnlyOwnCampus() throws Exception {
        seed("campus_a");
        seed("campus_b");

        mockMvc.perform(get("/api/campuses")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("campus_a"));
    }

    @Test
    void adminListsAllCampuses() throws Exception {
        seed("campus_a");
        seed("campus_b");

        mockMvc.perform(get("/api/campuses")
                .with(authentication(as(UserRole.ADMIN, "campus_hq"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }
}
