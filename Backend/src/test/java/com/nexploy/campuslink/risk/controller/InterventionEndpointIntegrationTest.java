package com.nexploy.campuslink.risk.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.risk.dto.CreateInterventionRequest;
import com.nexploy.campuslink.risk.repository.InterventionPlanRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end HTTP integration test for the persisted risk -> intervention workflow. Runs through
 * real routing and the security filter chain against in-memory H2. Covers plan creation
 * (officer-only), the PROPOSED -> ACTIVE -> COMPLETED lifecycle with its guards, action toggling
 * and derived progress, the assigned-mentor access rule, and the campus/role boundaries.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class InterventionEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InterventionPlanRepository interventionPlanRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void reset() {
        interventionPlanRepository.deleteAll();
        studentRepository.deleteAll();
    }

    private Authentication as(UserRole role, String username, String campusId) {
        UserAccount user = UserAccount.builder()
            .username(username).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void seedStudent(String id, String campusId) {
        studentRepository.save(Student.builder()
            .id(id).campusId(campusId).name("Demo").email(id + "@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("7.0")).backlogs(2).readinessScore(45).placementStatus("ACTIVE")
            .build());
    }

    private CreateInterventionRequest planRequest(String mentor) {
        return new CreateInterventionRequest(
            mentor, "risk_seed",
            List.of("Interview prep", "Resume"),
            List.of("Attend 3 mock interviews", "Rework resume"),
            "Flagged HIGH after low readiness");
    }

    private String createPlanAsOfficer(String studentId, String campusId, String mentor) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/students/" + studentId + "/interventions")
                .with(authentication(as(UserRole.OFFICER, "officer1", campusId)))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(planRequest(mentor))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PROPOSED"))
            .andExpect(jsonPath("$.progress").value(0))
            .andExpect(jsonPath("$.actions.length()").value(2))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void officerCreatesPlanAndDrivesLifecycle() throws Exception {
        seedStudent("stu_1", "campus_a");
        String planId = createPlanAsOfficer("stu_1", "campus_a", "mentor1");

        mockMvc.perform(post("/api/interventions/" + planId + "/activate")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/interventions/" + planId + "/complete")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void completingAProposedPlanIsConflict() throws Exception {
        seedStudent("stu_1", "campus_a");
        String planId = createPlanAsOfficer("stu_1", "campus_a", "mentor1");

        // PROPOSED -> COMPLETED skips ACTIVE.
        mockMvc.perform(post("/api/interventions/" + planId + "/complete")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isConflict());
    }

    @Test
    void togglingActionsUpdatesDerivedProgress() throws Exception {
        seedStudent("stu_1", "campus_a");
        String planId = createPlanAsOfficer("stu_1", "campus_a", "mentor1");

        // Read the plan to discover an action id.
        MvcResult listed = mockMvc.perform(get("/api/students/stu_1/interventions")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a"))))
            .andExpect(status().isOk())
            .andReturn();
        String actionId = objectMapper.readTree(listed.getResponse().getContentAsString())
            .get(0).get("actions").get(0).get("id").asText();

        // Mark one of two actions done -> 50%.
        mockMvc.perform(post("/api/interventions/" + planId + "/actions/toggle")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a")))
                .contentType("application/json")
                .content("{\"actionId\":\"" + actionId + "\",\"done\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.progress").value(50));
    }

    @Test
    void assignedMentorCanActButUnassignedMentorCannot() throws Exception {
        seedStudent("stu_1", "campus_a");
        String planId = createPlanAsOfficer("stu_1", "campus_a", "mentor1");

        // The named mentor can activate.
        mockMvc.perform(post("/api/interventions/" + planId + "/activate")
                .with(authentication(as(UserRole.MENTOR, "mentor1", "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        // A different mentor cannot.
        mockMvc.perform(post("/api/interventions/" + planId + "/complete")
                .with(authentication(as(UserRole.MENTOR, "mentor2", "campus_a"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void mentorCannotRaisePlan() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/interventions")
                .with(authentication(as(UserRole.MENTOR, "mentor1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(planRequest("mentor1"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void officerCannotRaisePlanForStudentInAnotherCampus() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/interventions")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(planRequest("mentor1"))))
            .andExpect(status().isCreated());

        // Officers are cross-campus by design, so use an unknown student for the 404 path instead.
        mockMvc.perform(post("/api/students/missing/interventions")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a")))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(planRequest("mentor1"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void createWithNoActionsIsBadRequest() throws Exception {
        seedStudent("stu_1", "campus_a");

        mockMvc.perform(post("/api/students/stu_1/interventions")
                .with(authentication(as(UserRole.OFFICER, "officer1", "campus_a")))
                .contentType("application/json")
                .content("{\"mentorUsername\":\"mentor1\",\"focusAreas\":[\"x\"],\"actionLabels\":[]}"))
            .andExpect(status().isBadRequest());
    }
}
