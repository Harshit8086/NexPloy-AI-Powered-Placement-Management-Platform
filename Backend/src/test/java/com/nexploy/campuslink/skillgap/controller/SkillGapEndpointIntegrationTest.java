package com.nexploy.campuslink.skillgap.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * End-to-end HTTP integration test for the deterministic skill-gap endpoint (PRD section 5.2). It
 * runs through real request routing, request-body validation, the security filter chain, and
 * campus-scope enforcement against in-memory H2. The analysis is DB-only (no ML call), so it is
 * fully exercisable here.
 *
 * <p>Covers: the JSON body shape the frontend maps (camelCase, prioritised skillGaps), met
 * requirements dropped, dual campus-scope, unknown student, validation, and the unauthenticated
 * path.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SkillGapEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentSkillRepository studentSkillRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BODY = """
        {
          "targetRole": "Backend Engineer",
          "skills": [
            { "skill": "AWS", "currentLevel": 0, "requiredLevel": 60 },
            { "skill": "Docker", "currentLevel": 45, "requiredLevel": 70 },
            { "skill": "Java", "currentLevel": 85, "requiredLevel": 80 }
          ]
        }
        """;

    @BeforeEach
    void seed() {
        studentSkillRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(student("stu_1", "campus_a"));
    }

    private Student student(String id, String campusId) {
        return Student.builder()
            .id(id).campusId(campusId).name("Demo").email("d@e.example")
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .placementStatus("ACTIVE").build();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void returnsPrioritisedSkillGapsInTheFrontendContractShape() throws Exception {
        // AWS gap 60 -> HIGH (leads), Docker gap 25 -> MEDIUM, Java already meets -> dropped.
        mockMvc.perform(post("/api/students/stu_1/skill-gap")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.studentId").value("stu_1"))
            .andExpect(jsonPath("$.targetRole").value("Backend Engineer"))
            .andExpect(jsonPath("$.skillGaps.length()").value(2))
            .andExpect(jsonPath("$.skillGaps[0].skill").value("AWS"))
            .andExpect(jsonPath("$.skillGaps[0].currentLevel").value(0))
            .andExpect(jsonPath("$.skillGaps[0].targetLevel").value(60))
            .andExpect(jsonPath("$.skillGaps[0].currentLabel").value("None"))
            .andExpect(jsonPath("$.skillGaps[0].targetLabel").value("Advanced"))
            .andExpect(jsonPath("$.skillGaps[0].priority").value("HIGH"))
            .andExpect(jsonPath("$.skillGaps[0].whyItMatters").isNotEmpty())
            .andExpect(jsonPath("$.skillGaps[0].recommendedAction").isNotEmpty())
            .andExpect(jsonPath("$.skillGaps[1].skill").value("Docker"))
            .andExpect(jsonPath("$.skillGaps[1].priority").value("MEDIUM"));
    }

    @Test
    void sourcesCurrentLevelFromStoredSkillsWhenBodyOmitsIt() throws Exception {
        // The student's stored skills feed currentLevel: Java 85 meets the 80 requirement (skipped),
        // AWS is not recorded so it resolves to 0 -> a full HIGH gap.
        studentSkillRepository.save(StudentSkill.builder()
            .id("sk_java").studentId("stu_1").campusId("campus_a")
            .skill("Java").proficiency(85).source("SELF_REPORTED")
            .updatedAt(OffsetDateTime.now()).build());

        String body = """
            {
              "targetRole": "Backend Engineer",
              "skills": [
                { "skill": "Java", "requiredLevel": 80 },
                { "skill": "AWS", "requiredLevel": 60 }
              ]
            }
            """;

        mockMvc.perform(post("/api/students/stu_1/skill-gap")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skillGaps.length()").value(1))
            .andExpect(jsonPath("$.skillGaps[0].skill").value("AWS"))
            .andExpect(jsonPath("$.skillGaps[0].currentLevel").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.skillGaps[0].evidenceStatus").value("NO_EVIDENCE"))
            .andExpect(jsonPath("$.skillGaps[0].priority").value("HIGH"));
    }

    @Test
    void recruiterInSameCampusIsAllowed() throws Exception {
        mockMvc.perform(post("/api/students/stu_1/skill-gap")
                .with(authentication(as(UserRole.RECRUITER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    void recruiterOutsideCampusIsForbidden() throws Exception {
        mockMvc.perform(post("/api/students/stu_1/skill-gap")
                .with(authentication(as(UserRole.RECRUITER, "campus_other")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void unknownStudentReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/students/missing/skill-gap")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void emptySkillsListIsRejectedAsBadRequest() throws Exception {
        String invalid = "{ \"targetRole\": \"SDE\", \"skills\": [] }";
        mockMvc.perform(post("/api/students/stu_1/skill-gap")
                .with(authentication(as(UserRole.OFFICER, "campus_a")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalid))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/students/stu_1/skill-gap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().is4xxClientError());
    }
}
