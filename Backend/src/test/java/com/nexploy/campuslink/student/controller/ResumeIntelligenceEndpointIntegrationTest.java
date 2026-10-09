package com.nexploy.campuslink.student.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.ResumeAnalysisRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP test for GET /api/students/me/resume-intelligence. Confirms the read-only resume
 * summary resolves the signed-in student by email == username, exposes the authoritative stored
 * resume score, derives strengths/missing-keywords from the student's own recorded data, and is
 * rejected when unauthenticated. Runs against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ResumeIntelligenceEndpointIntegrationTest {

    private static final String EMAIL = "student@campus.example";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        resumeAnalysisRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(82).resumeScore(74)
            .strengthsJson("[\"Java\",\"Spring Boot\",\"React\",\"SQL\",\"Docker\"]")
            .criticalGapsJson("[\"System Design\",\"Kubernetes\"]")
            .targetRole("Backend Developer").placementStatus("ACTIVE").build());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void returnsScoreStrengthsAndMissingKeywords() throws Exception {
        mockMvc.perform(get("/api/students/me/resume-intelligence").with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score").value(74))
            // strengths capped at four
            .andExpect(jsonPath("$.strengths.length()").value(4))
            .andExpect(jsonPath("$.strengths[0]").value("Java"))
            // missing keywords are exactly the recorded critical gaps — nothing appended or fabricated
            .andExpect(jsonPath("$.missingKeywords.length()").value(2))
            .andExpect(jsonPath("$.missingKeywords[0]").value("System Design"))
            .andExpect(jsonPath("$.missingKeywords[1]").value("Kubernetes"))
            // the pre-upload summary carries no fabricated weaknesses/suggestions; those are per-resume
            .andExpect(jsonPath("$.weaknesses.length()").value(0))
            .andExpect(jsonPath("$.projectSuggestions.length()").value(0))
            .andExpect(jsonPath("$.recommendation").isNotEmpty());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/resume-intelligence"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void analyzingAResumePersistsItToHistoryAndDetailIsRetrievable() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "jane_resume.txt", MediaType.TEXT_PLAIN_VALUE,
            ("Backend Developer. Built a REST API with Spring Boot and SQL on PostgreSQL. "
                + "Reduced p95 latency by 40%. Deployed on AWS with Docker and CI/CD. "
                + "Added JUnit tests reaching 85% coverage. github.com/jane/project")
                .getBytes(StandardCharsets.UTF_8));

        // Analyze once — the structured result is returned and a history row is written.
        String analyzeBody = mockMvc.perform(multipart("/api/students/me/resume-intelligence/analyze")
                .file(file).with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overallScore").isNumber())
            // §10 Resume Quality is nested into the analyze response, grounded and vocabulary-constrained.
            .andExpect(jsonPath("$.resumeQuality").exists())
            .andExpect(jsonPath("$.resumeQuality.structure.status").isNotEmpty())
            .andExpect(jsonPath("$.resumeQuality.topImprovements").isArray())
            .andReturn().getResponse().getContentAsString();
        int analyzedScore = objectMapper.readTree(analyzeBody).get("overallScore").asInt();

        // History lists exactly that run, newest first, with the resume's filename and score.
        String historyBody = mockMvc.perform(get("/api/students/me/resume-analysis/history")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].resumeName").value("jane_resume.txt"))
            .andExpect(jsonPath("$[0].overallScore").value(analyzedScore))
            .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(historyBody).get(0).get("id").asText();

        // Detail restores the full structured analysis for that run.
        mockMvc.perform(get("/api/students/me/resume-analysis/history/" + id)
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.resumeName").value("jane_resume.txt"))
            .andExpect(jsonPath("$.analysis.overallScore").value(analyzedScore))
            .andExpect(jsonPath("$.analysis.metrics").isArray())
            .andExpect(jsonPath("$.analysis.skillMatch").isArray())
            .andExpect(jsonPath("$.analysis.resumeQuality.structure.status").isNotEmpty());
    }

    @Test
    void historyIsEmptyBeforeAnyAnalysisAndUnknownDetailIs404() throws Exception {
        mockMvc.perform(get("/api/students/me/resume-analysis/history")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/students/me/resume-analysis/history/does_not_exist")
                .with(authentication(asStudent(EMAIL))))
            .andExpect(status().isNotFound());
    }
}
