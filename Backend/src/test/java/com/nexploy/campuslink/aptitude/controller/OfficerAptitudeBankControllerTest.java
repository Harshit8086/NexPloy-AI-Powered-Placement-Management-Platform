package com.nexploy.campuslink.aptitude.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.aptitude.dto.AddBankQuestionRequest;
import com.nexploy.campuslink.aptitude.dto.SetQuestionActiveRequest;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestionSource;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptQuestionRepository;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end HTTP integration test for the officer-facing question bank: role guard (403 for
 * students), add/list/deactivate, campus scoping, and the wall between the bank and private
 * AI-generated practice questions. Deactivation is verified end to end through the student catalog,
 * where it flips the affected test to unavailable.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OfficerAptitudeBankControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AptitudeQuestionRepository questionRepository;
    @Autowired private AptitudeAttemptRepository attemptRepository;
    @Autowired private AptitudeAttemptQuestionRepository attemptQuestionRepository;
    @Autowired private AssessmentRecordRepository assessmentRecordRepository;

    private static final String STUDENT_EMAIL = "student@e.example";
    private static final String[] OPTION_CYCLE = {"A", "B", "C", "D"};

    @BeforeEach
    void seed() {
        assessmentRecordRepository.deleteAll();
        attemptQuestionRepository.deleteAll();
        attemptRepository.deleteAll();
        questionRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_1").campusId("campus_a").name("Student").email(STUDENT_EMAIL)
            .rollNumber("R-1").branch("CSE").graduationYear(2026)
            .cgpa(new BigDecimal("8.0")).backlogs(0).readinessScore(75)
            .placementStatus("ACTIVE").build());

        seedBankQuestions(15);
    }

    @Test
    void officerAddsAndListsBankQuestions() throws Exception {
        String body = mockMvc.perform(post("/api/aptitude/bank")
                .with(authentication(asOfficer()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddBankQuestionRequest(
                    "QUANTITATIVE", "HARD", "Officer authored question",
                    "One", "Two", "Three", "Four", "B", "Because two."))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source").value("OFFICER"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.category").value("QUANTITATIVE"))
            .andReturn().getResponse().getContentAsString();
        String addedId = objectMapper.readTree(body).get("id").asText();

        String list = mockMvc.perform(get("/api/aptitude/bank")
                .with(authentication(asOfficer())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(list).findValues("id"))
            .anyMatch(node -> node.asText().equals(addedId));
    }

    @Test
    void studentIsForbiddenFromTheBank() throws Exception {
        mockMvc.perform(get("/api/aptitude/bank")
                .with(authentication(asStudent(STUDENT_EMAIL))))
            .andExpect(status().isForbidden());
    }

    @Test
    void deactivatingAQuestionExcludesItFromOfficialDraws() throws Exception {
        // 15 seeded -> QUANTITATIVE available.
        assertThat(testAvailability("QUANTITATIVE")).isTrue();

        mockMvc.perform(patch("/api/aptitude/bank/apq_test_q1/active")
                .with(authentication(asOfficer()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetQuestionActiveRequest(false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        // 14 active < 15 required -> the catalog flips the test to unavailable.
        assertThat(testAvailability("QUANTITATIVE")).isFalse();
    }

    @Test
    void bankListNeverShowsStudentOwnedAiQuestions() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        questionRepository.save(AptitudeQuestion.builder()
            .id("apq_private_ai")
            .campusId("campus_a")
            .studentId("stu_1")
            .source(AptitudeQuestionSource.AI_GENERATED)
            .category("QUANTITATIVE")
            .prompt("A private AI practice question")
            .optionA("One").optionB("Two").optionC("Three").optionD("Four")
            .correctOption("A")
            .active(true)
            .createdAt(now).updatedAt(now)
            .build());

        String list = mockMvc.perform(get("/api/aptitude/bank")
                .with(authentication(asOfficer())))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(list).findValues("id"))
            .noneMatch(node -> node.asText().equals("apq_private_ai"));
    }

    @Test
    void anotherCampusQuestionReadsAsNotFound() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        questionRepository.save(AptitudeQuestion.builder()
            .id("apq_other_campus")
            .campusId("campus_b")
            .source(AptitudeQuestionSource.OFFICER)
            .category("QUANTITATIVE")
            .prompt("Another campus's question")
            .optionA("One").optionB("Two").optionC("Three").optionD("Four")
            .correctOption("A")
            .active(true)
            .createdAt(now).updatedAt(now)
            .build());

        mockMvc.perform(patch("/api/aptitude/bank/apq_other_campus/active")
                .with(authentication(asOfficer()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SetQuestionActiveRequest(false))))
            .andExpect(status().isNotFound());
    }

    @Test
    void unknownCategoryIsRejected() throws Exception {
        mockMvc.perform(post("/api/aptitude/bank")
                .with(authentication(asOfficer()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddBankQuestionRequest(
                    "CHEMISTRY", null, "Prompt", "A", "B", "C", "D", "A", null))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidCorrectOptionFailsValidation() throws Exception {
        mockMvc.perform(post("/api/aptitude/bank")
                .with(authentication(asOfficer()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddBankQuestionRequest(
                    "QUANTITATIVE", null, "Prompt", "A", "B", "C", "D", "E", null))))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private void seedBankQuestions(int count) {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 1; i <= count; i++) {
            questionRepository.save(AptitudeQuestion.builder()
                .id("apq_test_q" + i)
                .source(AptitudeQuestionSource.SEEDED)
                .category("QUANTITATIVE")
                .difficulty("EASY")
                .prompt("Quant question " + i)
                .optionA("First option").optionB("Second option")
                .optionC("Third option").optionD("Fourth option")
                .correctOption(OPTION_CYCLE[(i - 1) % OPTION_CYCLE.length])
                .explanation("Explanation " + i)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        }
    }

    private boolean testAvailability(String testId) throws Exception {
        String body = mockMvc.perform(get("/api/students/me/aptitude/tests")
                .with(authentication(asStudent(STUDENT_EMAIL))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        for (JsonNode test : objectMapper.readTree(body)) {
            if (testId.equals(test.get("testId").asText())) {
                return test.get("available").asBoolean();
            }
        }
        throw new AssertionError("Test not found in catalog response: " + testId);
    }

    private Authentication asOfficer() {
        UserAccount user = UserAccount.builder()
            .username("officer@e.example").password("{noop}secret").campusId("campus_a")
            .role(UserRole.OFFICER).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}