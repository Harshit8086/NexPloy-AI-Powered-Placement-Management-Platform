package com.nexploy.campuslink.interview.controller;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.interview.dto.StartMockInterviewRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerRequest;
import com.nexploy.campuslink.interview.entity.MockInterviewType;
import com.nexploy.campuslink.interview.repository.MockInterviewQuestionRepository;
import com.nexploy.campuslink.interview.repository.MockInterviewRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the student mock-interview endpoints against in-memory H2:
 * real routing, the security filter chain, and STUDENT-owner scoping. The test profile leaves {@code
 * nexploy.ai.provider} unset, so the offline rule-based adapter drives question generation and
 * scoring — proving the flow works with no AI key configured.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudentMockInterviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StudentRepository studentRepository;
    @Autowired private MockInterviewRepository interviewRepository;
    @Autowired private MockInterviewQuestionRepository questionRepository;
    @Autowired @Qualifier("readinessRecomputeExecutor")
    private ThreadPoolTaskExecutor recomputeExecutor;

    private static final String OWNER_EMAIL = "owner@e.example";
    private static final String OTHER_EMAIL = "other@e.example";

    @BeforeEach
    void seed() {
        // Completing a mock interview feeds the shared readiness recompute pool, which saves
        // student rows asynchronously; drain it before reseeding so a stale async save cannot
        // collide with the fresh seed rows (duplicate "stu_owner" key).
        await().atMost(Duration.ofSeconds(10)).until(() ->
            recomputeExecutor.getActiveCount() == 0
                && recomputeExecutor.getThreadPoolExecutor().getQueue().isEmpty());

        questionRepository.deleteAll();
        interviewRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_owner").campusId("campus_a").name("Owner").email(OWNER_EMAIL)
            .rollNumber("R-1").branch("CSE").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .placementStatus("ACTIVE").targetRole("Backend Developer").build());
        studentRepository.save(Student.builder()
            .id("stu_other").campusId("campus_a").name("Other").email(OTHER_EMAIL)
            .rollNumber("R-2").branch("CSE").graduationYear(2026)
            .cgpa(new BigDecimal("7.5")).backlogs(0).readinessScore(70)
            .placementStatus("ACTIVE").build());
    }

    @Test
    void studentStartsAnInterviewAndGetsQuestions() throws Exception {
        mockMvc.perform(post("/api/students/me/mock-interviews")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new StartMockInterviewRequest(MockInterviewType.BEHAVIOURAL, "Backend Developer", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.currentQuestion").value(0))
            .andExpect(jsonPath("$.questions.length()").value(6))
            .andExpect(jsonPath("$.questions[0].prompt").isNotEmpty());
    }

    @Test
    void ownerCanAnswerAndCompleteThenReadReport() throws Exception {
        String id = startInterview();

        // Answer the first question; response reports the new position.
        mockMvc.perform(post("/api/students/me/mock-interviews/" + id + "/answers")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SubmitAnswerRequest("I led the checkout rewrite and cut latency by 40 percent."))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordinal").value(0))
            .andExpect(jsonPath("$.currentQuestion").value(1))
            .andExpect(jsonPath("$.feedback.score").isNumber());

        // Complete early: report is finalised and COMPLETED.
        mockMvc.perform(post("/api/students/me/mock-interviews/" + id + "/complete")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.overallScore").isNumber());

        // History lists it for the owner.
        mockMvc.perform(get("/api/students/me/mock-interviews")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void anotherStudentCannotReadSomeoneElsesInterview() throws Exception {
        String id = startInterview();

        mockMvc.perform(get("/api/students/me/mock-interviews/" + id)
                .with(authentication(asStudent(OTHER_EMAIL))))
            .andExpect(status().isNotFound());
    }

    @Test
    void anotherStudentCannotAnswerSomeoneElsesInterview() throws Exception {
        String id = startInterview();

        mockMvc.perform(post("/api/students/me/mock-interviews/" + id + "/answers")
                .with(authentication(asStudent(OTHER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitAnswerRequest("Trying to hijack."))))
            .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/mock-interviews"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void validationRejectsMissingType() throws Exception {
        mockMvc.perform(post("/api/students/me/mock-interviews")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"Backend Developer\",\"useResume\":false}"))
            .andExpect(status().isBadRequest());
    }

    private String startInterview() throws Exception {
        String body = mockMvc.perform(post("/api/students/me/mock-interviews")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new StartMockInterviewRequest(MockInterviewType.BEHAVIOURAL, "Backend Developer", false))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
