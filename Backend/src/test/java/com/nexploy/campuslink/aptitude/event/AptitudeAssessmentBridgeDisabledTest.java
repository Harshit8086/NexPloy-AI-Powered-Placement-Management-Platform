package com.nexploy.campuslink.aptitude.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.aptitude.dto.SaveAnswerRequest;
import com.nexploy.campuslink.aptitude.dto.StartAttemptRequest;
import com.nexploy.campuslink.aptitude.dto.SubmitAttemptRequest;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestionSource;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptQuestionRepository;
import com.nexploy.campuslink.aptitude.repository.AptitudeQuestionRepository;
import com.nexploy.campuslink.assessment.repository.AssessmentRecordRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The feed gate in isolation: with {@code nexploy.readiness.aptitude.feeds-assessment=false} the
 * bridge bean does not exist at all, so even a completed OFFICIAL attempt — which publishes the
 * completion event — leaves assessment_records untouched.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = "nexploy.readiness.aptitude.feeds-assessment=false")
class AptitudeAssessmentBridgeDisabledTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AptitudeQuestionRepository questionRepository;
    @Autowired private AptitudeAttemptQuestionRepository attemptQuestionRepository;
    @Autowired private AssessmentRecordRepository assessmentRecordRepository;

    private static final String STUDENT_EMAIL = "student@e.example";

    @BeforeEach
    void seed() {
        assessmentRecordRepository.deleteAll();
        attemptQuestionRepository.deleteAll();
        questionRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Student").email(STUDENT_EMAIL)
            .rollNumber("R-1").branch("CSE").graduationYear(2026)
            .cgpa(new BigDecimal("8.0")).backlogs(0).readinessScore(75)
            .placementStatus("ACTIVE").assessmentAptitude(40).build());

        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 1; i <= 15; i++) {
            questionRepository.save(AptitudeQuestion.builder()
                .id("apq_gate_q" + i)
                .source(AptitudeQuestionSource.SEEDED)
                .category("QUANTITATIVE")
                .prompt("Quant question " + i)
                .optionA("First").optionB("Second").optionC("Third").optionD("Fourth")
                .correctOption("A")
                .active(true)
                .createdAt(now).updatedAt(now)
                .build());
        }
    }

    @Test
    void theBridgeBeanIsAbsent() {
        assertThat(applicationContext.getBeanNamesForType(AptitudeAssessmentBridge.class)).isEmpty();
    }

    @Test
    void officialSubmitWritesNoAssessmentRow() throws Exception {
        String startBody = mockMvc.perform(post("/api/students/me/aptitude/attempts")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartAttemptRequest("QUANTITATIVE"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode started = objectMapper.readTree(startBody);
        String attemptId = started.get("attemptId").asText();
        String questionRowId = started.get("questions").get(0).get("attemptQuestionId").asText();

        mockMvc.perform(put("/api/students/me/aptitude/attempts/" + attemptId + "/answers")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SaveAnswerRequest(questionRowId, "A", false))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/submit")
                .with(authentication(asStudent()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(60))))
            .andExpect(status().isOk())
            .andExpect(jsonPathOk());

        // The event was published but has no listener; give any stray async work a moment anyway.
        Thread.sleep(500);
        assertThat(assessmentRecordRepository.findByStudentId("stu_1")).isEmpty();
        assertThat(studentRepository.findById("stu_1").orElseThrow().getAssessmentAptitude())
            .isEqualTo(40);
    }

    private static org.springframework.test.web.servlet.ResultMatcher jsonPathOk() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers
            .jsonPath("$.status").value("COMPLETED");
    }

    private Authentication asStudent() {
        UserAccount user = UserAccount.builder()
            .username(STUDENT_EMAIL).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}