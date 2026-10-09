package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for {@code GET /api/students/me/learning-recommendations}. Confirms the
 * endpoint requires authentication, derives recommendations from the signed-in student's own
 * roadmap gaps (target-role scoped), surfaces only curated resources with real https URLs, ties each
 * gap to a roadmap milestone, and reflects an already-added portfolio project via {@code alreadyAdded}.
 * Runs against H2 with the rule-based provider.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class LearningRecommendationEndpointIntegrationTest {

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
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/learning-recommendations"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void recommendsPendingGapsWithCuratedResourcesTiedToMilestones() throws Exception {
        mockMvc.perform(get("/api/students/me/learning-recommendations").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            // Backend roadmap has pending gaps (SQL is seeded DONE from the strength, so it is absent).
            .andExpect(jsonPath("$[0].skill").exists())
            .andExpect(jsonPath("$[0].relatedMilestone.taskId").exists())
            .andExpect(jsonPath("$[0].relatedMilestone.phaseKey").exists())
            .andExpect(jsonPath("$[0].addToRoadmap.name").exists())
            // Any curated resource must be a real https URL — never an invented link.
            .andExpect(jsonPath("$[*].resources[*].url").value(
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.startsWith("https://"))))
            // No recommendation for SQL, which the seeded strength already demonstrates.
            .andExpect(jsonPath("$[?(@.skill == 'SQL')]").isEmpty());
    }

    @Test
    void alreadyAddedTracksAProjectAddedToTheRoadmap() throws Exception {
        // Materialize the roadmap, then add a REST API showcase project through the real endpoint.
        mockMvc.perform(get("/api/students/me/roadmap").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk());
        String project = "{\"name\":\"REST API Showcase Project\",\"description\":\"Build it\","
            + "\"addressesGap\":\"REST API\",\"skillsDemonstrated\":[\"REST API\"],\"difficulty\":\"Intermediate\"}";
        mockMvc.perform(post("/api/students/me/roadmap/projects")
                .with(authentication(asStudent(EMAIL)))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(project))
            .andExpect(status().isOk());

        String body = mockMvc.perform(
                get("/api/students/me/learning-recommendations").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        var recs = objectMapper.readTree(body);
        boolean restAlreadyAdded = false;
        for (var rec : recs) {
            if ("REST API".equals(rec.get("skill").asText())) {
                restAlreadyAdded = rec.get("alreadyAdded").asBoolean();
            }
        }
        org.assertj.core.api.Assertions.assertThat(restAlreadyAdded).isTrue();
    }
}
