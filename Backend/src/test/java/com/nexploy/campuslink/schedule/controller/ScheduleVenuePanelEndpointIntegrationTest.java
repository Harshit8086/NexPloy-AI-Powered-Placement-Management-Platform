package com.nexploy.campuslink.schedule.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.schedule.dto.CreatePanelRequest;
import com.nexploy.campuslink.schedule.dto.CreatePanelRequest.PanelMemberInput;
import com.nexploy.campuslink.schedule.dto.CreateVenueRequest;
import com.nexploy.campuslink.schedule.dto.SetActiveRequest;
import com.nexploy.campuslink.schedule.dto.UpdatePanelRequest;
import com.nexploy.campuslink.schedule.dto.UpdateVenueRequest;
import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
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
 * HTTP integration tests for the interview venue/panel admin endpoints
 * ({@code /api/schedule/venues}, {@code /api/schedule/panels}): POST create, PUT update, PATCH
 * active. Officer/admin only; other roles are forbidden.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ScheduleVenuePanelEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private PanelRepository panelRepository;

    @Autowired
    private PanelMemberRepository panelMemberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        panelMemberRepository.deleteAll();
        panelRepository.deleteAll();
        venueRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Venue seedVenue(String id, String campusId) {
        return venueRepository.save(Venue.builder()
            .id(id).campusId(campusId).name("Old Hall").capacity(50).active(true).build());
    }

    private Panel seedPanel(String id, String campusId) {
        return panelRepository.save(Panel.builder()
            .id(id).campusId(campusId).name("Old Panel").active(true).build());
    }

    @Test
    void officerCreatesVenue() throws Exception {
        CreateVenueRequest request = new CreateVenueRequest("Auditorium A", 120, "campus_a");

        mockMvc.perform(post("/api/schedule/venues")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Auditorium A"))
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.active").value(true));
    }

    /** Officers are pinned to their assigned campus: a foreign campusId in the request body is ignored, not honored. */
    @Test
    void officerCreateVenueIgnoresForeignCampusId() throws Exception {
        CreateVenueRequest request = new CreateVenueRequest("Auditorium B", 120, "campus_b");

        mockMvc.perform(post("/api/schedule/venues")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.campusId").value("campus_a"));
    }

    @Test
    void officerCannotUpdateAnotherCampusVenue() throws Exception {
        seedVenue("venue_1", "campus_b");
        UpdateVenueRequest request = new UpdateVenueRequest("Renamed Hall", 80);

        mockMvc.perform(put("/api/schedule/venues/venue_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotCreateVenue() throws Exception {
        CreateVenueRequest request = new CreateVenueRequest("Auditorium A", 120, "campus_a");

        mockMvc.perform(post("/api/schedule/venues")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerUpdatesVenue() throws Exception {
        seedVenue("venue_1", "campus_a");
        UpdateVenueRequest request = new UpdateVenueRequest("Renamed Hall", 80);

        mockMvc.perform(put("/api/schedule/venues/venue_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed Hall"))
            .andExpect(jsonPath("$.capacity").value(80));
    }

    @Test
    void officerDeactivatesVenue() throws Exception {
        seedVenue("venue_1", "campus_a");

        mockMvc.perform(patch("/api/schedule/venues/venue_1/active")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SetActiveRequest(false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void officerCreatesPanel() throws Exception {
        CreatePanelRequest request = new CreatePanelRequest(
            "Panel Alpha", "campus_a",
            List.of(new PanelMemberInput("Reviewer One", "r1@campus.example")));

        mockMvc.perform(post("/api/schedule/panels")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Panel Alpha"))
            .andExpect(jsonPath("$.campusId").value("campus_a"))
            .andExpect(jsonPath("$.members[0].name").value("Reviewer One"));
    }

    /** Officers are pinned to their assigned campus: a foreign campusId in the request body is ignored, not honored. */
    @Test
    void officerCreatePanelIgnoresForeignCampusId() throws Exception {
        CreatePanelRequest request = new CreatePanelRequest("Panel Beta", "campus_b", null);

        mockMvc.perform(post("/api/schedule/panels")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.campusId").value("campus_a"));
    }

    @Test
    void officerCannotUpdateAnotherCampusPanel() throws Exception {
        seedPanel("panel_1", "campus_b");
        UpdatePanelRequest request = new UpdatePanelRequest("Hijacked Panel", null);

        mockMvc.perform(put("/api/schedule/panels/panel_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotCreatePanel() throws Exception {
        CreatePanelRequest request = new CreatePanelRequest("Panel Alpha", "campus_a", null);

        mockMvc.perform(post("/api/schedule/panels")
                .with(authentication(as(UserRole.STUDENT, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerUpdatesPanel() throws Exception {
        seedPanel("panel_1", "campus_a");
        UpdatePanelRequest request = new UpdatePanelRequest(
            "Panel Beta", List.of(new PanelMemberInput("Reviewer Two", "r2@campus.example")));

        mockMvc.perform(put("/api/schedule/panels/panel_1")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Panel Beta"))
            .andExpect(jsonPath("$.members[0].name").value("Reviewer Two"));
    }
}
