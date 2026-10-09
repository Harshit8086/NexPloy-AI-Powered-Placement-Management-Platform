package com.nexploy.campuslink.skillviva.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
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
 * End-to-end HTTP test for the AI Skill Viva + evidence attach. Runs on H2 with the rule-based AI
 * adapter (test profile), so the MCQ set for "Java" is deterministic: picking the correct option
 * text scores 100 per question, the average recalibrates the stored level, and the skill becomes
 * VERIFIED. The main flow echoes the vivaId from /start so submit scores against the stored copy
 * (the production path, which also works for the non-deterministic LLM adapter). Ownership is
 * enforced by email.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SkillVivaEndpointIntegrationTest {

    private static final String EMAIL = "viva.student@campus.example";
    private static final String SKILL_ID = "sk_viva_1";

    // The rule-based adapter's MCQ set with "{skill}" replaced by "Java" — deterministic, so the
    // test can submit the exact option texts the server holds and assert the measured score.
    private static final List<String> PROMPTS = List.of(
        "Which best describes how you apply Java day to day?",
        "A teammate asks you to explain a core concept of Java. What do you do?",
        "When debugging a problem in Java, what is your first move?",
        "Which habit most reduces mistakes when working with Java?",
        "How would you confirm someone genuinely knows Java?");

    private static final List<String> CORRECT = List.of(
        "I use it deliberately, choosing approaches that fit the problem's constraints",
        "Explain the why and the trade-offs, and check their understanding",
        "Reproduce it and isolate where the behaviour breaks",
        "Small changes with review and testing as you go",
        "Ask them to reason through a real scenario and defend their choices");

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentSkillRepository studentSkillRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        studentSkillRepository.deleteAll();
        studentRepository.deleteAll();
        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Viva Demo").email(EMAIL)
            .rollNumber("R-1").branch("CSE").graduationYear(2026).placementStatus("ACTIVE").build());
        studentSkillRepository.save(StudentSkill.builder()
            .id(SKILL_ID).studentId("stu_1").campusId("campus_a").skill("Java")
            .proficiency(70).source("SELF_REPORTED").verificationStatus("SELF_DECLARED")
            .updatedAt(OffsetDateTime.now()).build());
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Test
    void startReturnsShuffledMcqs() throws Exception {
        mockMvc.perform(post("/api/students/me/skills/{id}/viva/start", SKILL_ID)
                .with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vivaId").isNotEmpty())
            .andExpect(jsonPath("$.skill").value("Java"))
            .andExpect(jsonPath("$.targetLevel").value(70))
            .andExpect(jsonPath("$.questions.length()").value(5))
            .andExpect(jsonPath("$.questions[0].prompt").isNotEmpty())
            .andExpect(jsonPath("$.questions[0].options.length()").value(4))
            .andExpect(jsonPath("$.questions[0].timerSeconds").value(45));
    }

    /** Runs the production flow: /start issues a vivaId; /submit echoes it back. */
    private String startViva() throws Exception {
        String json = mockMvc.perform(post("/api/students/me/skills/{id}/viva/start", SKILL_ID)
                .with(authentication(asStudent())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("vivaId").asText();
    }

    @Test
    void vivaRecalibratesAndVerifiesTheSkill() throws Exception {
        String vivaId = startViva();
        // First pick is a distractor (scored 0, correct option disclosed); the other four are the
        // correct option texts: 400 / 5 = 80.
        String body = objectMapper.writeValueAsString(Map.of(
            "vivaId", vivaId,
            "answers", List.of(
                Map.of("question", PROMPTS.get(0), "option", "I only follow step-by-step guides"),
                Map.of("question", PROMPTS.get(1), "option", CORRECT.get(1)),
                Map.of("question", PROMPTS.get(2), "option", CORRECT.get(2)),
                Map.of("question", PROMPTS.get(3), "option", CORRECT.get(3)),
                Map.of("question", PROMPTS.get(4), "option", CORRECT.get(4)))));

        mockMvc.perform(post("/api/students/me/skills/{id}/viva/submit", SKILL_ID)
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.achievedLevel").value(80))
            .andExpect(jsonPath("$.claimedLevel").value(70))
            .andExpect(jsonPath("$.feedback[0].score").value(0))
            .andExpect(jsonPath("$.feedback[0].correctOption").value(CORRECT.get(0)))
            .andExpect(jsonPath("$.feedback[1].score").value(100));

        StudentSkill updated = studentSkillRepository.findById(SKILL_ID).orElseThrow();
        Assertions.assertEquals("VERIFIED", updated.getVerificationStatus());
        // Stored proficiency recalibrated to the measured level (so the next viva targets it).
        Assertions.assertEquals(80, updated.getProficiency());
    }

    @Test
    void unknownVivaIdFallsBackToRegeneratedSet() throws Exception {
        // A vivaId the server doesn't hold (stale/foreign) degrades to regenerating the set —
        // exact for the deterministic rule-based adapter used in this test profile.
        String body = objectMapper.writeValueAsString(Map.of(
            "vivaId", "not-a-real-viva",
            "answers", List.of(
                Map.of("question", PROMPTS.get(0), "option", CORRECT.get(0)),
                Map.of("question", PROMPTS.get(1), "option", CORRECT.get(1)))));

        mockMvc.perform(post("/api/students/me/skills/{id}/viva/submit", SKILL_ID)
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.achievedLevel").value(100))
            .andExpect(jsonPath("$.feedback[0].score").value(100))
            .andExpect(jsonPath("$.feedback[1].score").value(100));
    }

    @Test
    void tamperedPromptScoresZero() throws Exception {
        // A prompt the server never generated cannot be matched: it scores 0 and drags the level down.
        String body = objectMapper.writeValueAsString(Map.of("answers", List.of(
            Map.of("question", "A question that was never asked", "option", "anything"))));

        mockMvc.perform(post("/api/students/me/skills/{id}/viva/submit", SKILL_ID)
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.achievedLevel").value(0))
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.feedback[0].score").value(0));

        StudentSkill updated = studentSkillRepository.findById(SKILL_ID).orElseThrow();
        Assertions.assertEquals(0, updated.getProficiency());
    }

    @Test
    void attachingProofMarksEvidenced() throws Exception {
        String body = objectMapper.writeValueAsString(
            Map.of("url", "https://github.com/demo/project", "note", "Built the module"));
        mockMvc.perform(put("/api/students/me/skills/{id}/evidence", SKILL_ID)
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationStatus").value("EVIDENCED"))
            .andExpect(jsonPath("$.evidenceUrl").value("https://github.com/demo/project"));
    }

    @Test
    void nonHttpsProofIsRejected() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("url", "http://insecure.example"));
        mockMvc.perform(put("/api/students/me/skills/{id}/evidence", SKILL_ID)
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
}