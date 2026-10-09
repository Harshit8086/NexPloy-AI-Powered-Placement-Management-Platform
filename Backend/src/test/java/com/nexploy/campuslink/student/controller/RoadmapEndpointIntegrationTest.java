package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.CareerRoadmapRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for the Career Roadmap endpoints. Confirms the roadmap materializes from the
 * curated catalog on first GET, that already-demonstrated skills are pre-completed, that toggling a
 * task recomputes progress, and that adding a project is idempotent — all scoped to the signed-in
 * student. Runs against H2 with the rule-based provider.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RoadmapEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CareerRoadmapRepository careerRoadmapRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        careerRoadmapRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(82).resumeScore(74)
            .strengthsJson("[\"SQL\"]")
            .criticalGapsJson("[\"System Design\"]")
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void materializesRoadmapWithDemonstratedSkillPreCompleted() throws Exception {
        mockMvc.perform(get("/api/students/me/roadmap").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.targetRole").value("Backend Developer"))
            .andExpect(jsonPath("$.phases").isArray())
            .andExpect(jsonPath("$.taskCount").isNumber())
            // The seeded strength "SQL" matches the backend catalog's SQL task -> at least one done.
            .andExpect(jsonPath("$.completedTaskCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.overallProgress").isNumber());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/roadmap"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void togglingATaskRecomputesProgressAndAddingAProjectIsIdempotent() throws Exception {
        String body = mockMvc.perform(get("/api/students/me/roadmap").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        int before = objectMapper.readTree(body).get("completedTaskCount").asInt();
        // Find a PLANNED task to complete.
        var phases = objectMapper.readTree(body).get("phases");
        String taskId = null;
        for (var phase : phases) {
            for (var task : phase.get("tasks")) {
                if ("PLANNED".equals(task.get("status").asText())) {
                    taskId = task.get("id").asText();
                    break;
                }
            }
            if (taskId != null) break;
        }

        mockMvc.perform(put("/api/students/me/roadmap/tasks/" + taskId)
                .with(authentication(asStudent(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedTaskCount").value(before + 1));

        // Add a project twice — projectCount stays at 1 (idempotent by title).
        String project = "{\"name\":\"Payments API\",\"description\":\"Build it\","
            + "\"addressesGap\":\"REST API\",\"skillsDemonstrated\":[\"REST API\"],\"difficulty\":\"Intermediate\"}";
        mockMvc.perform(post("/api/students/me/roadmap/projects")
                .with(authentication(asStudent(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON).content(project))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectCount").value(1));
        mockMvc.perform(post("/api/students/me/roadmap/projects")
                .with(authentication(asStudent(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON).content(project))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.projectCount").value(1));
    }

    @Test
    void regeneratePreservesCompletedTasks() throws Exception {
        // Materialize (SQL strength seeds one task DONE), then complete another PLANNED task.
        String body = mockMvc.perform(get("/api/students/me/roadmap").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        int seeded = objectMapper.readTree(body).get("completedTaskCount").asInt();
        String taskId = null;
        for (var phase : objectMapper.readTree(body).get("phases")) {
            for (var task : phase.get("tasks")) {
                if ("PLANNED".equals(task.get("status").asText())) {
                    taskId = task.get("id").asText();
                    break;
                }
            }
            if (taskId != null) break;
        }
        mockMvc.perform(put("/api/students/me/roadmap/tasks/" + taskId)
                .with(authentication(asStudent(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
            .andExpect(status().isOk());

        // Regenerating keeps the manual completion (and the seeded one) — never drops earned progress.
        mockMvc.perform(post("/api/students/me/roadmap/regenerate").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completedTaskCount").value(seeded + 1));
    }

    @Test
    void unknownTaskIdIsRejected() throws Exception {
        // Materialize first so the roadmap exists.
        mockMvc.perform(get("/api/students/me/roadmap").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/students/me/roadmap/tasks/does_not_exist")
                .with(authentication(asStudent(EMAIL)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void whatIfFactorsEndpointReturnsRoadmapFactors() throws Exception {
        mockMvc.perform(get("/api/students/me/what-if/factors").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.id == 'dsa')]").exists())
            .andExpect(jsonPath("$[?(@.id == 'mockInterviews')]").exists());
    }
}
