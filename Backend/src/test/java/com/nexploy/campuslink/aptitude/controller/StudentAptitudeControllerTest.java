package com.nexploy.campuslink.aptitude.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.aptitude.dto.SaveAnswerRequest;
import com.nexploy.campuslink.aptitude.dto.StartAttemptRequest;
import com.nexploy.campuslink.aptitude.dto.StartPracticeRequest;
import com.nexploy.campuslink.aptitude.dto.SubmitAttemptRequest;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestionSource;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptQuestionRepository;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptRepository;
import com.nexploy.campuslink.aptitude.repository.AptitudeQuestionRepository;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.repository.AssessmentRecordRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
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
 * End-to-end HTTP integration test for the student aptitude endpoints against in-memory H2: real
 * routing, the security filter chain, and STUDENT-owner scoping across both lanes. The test profile
 * forces the rule-based AI, so the practice lane runs fully offline. The bank is seeded per test
 * (Flyway is off in this profile), with 15 global QUANTITATIVE questions — exactly enough for the
 * QUANTITATIVE catalog test and nothing else, so availability flips are observable.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class StudentAptitudeControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AptitudeQuestionRepository questionRepository;
    @Autowired private AptitudeAttemptRepository attemptRepository;
    @Autowired private AptitudeAttemptQuestionRepository attemptQuestionRepository;
    @Autowired private AssessmentRecordRepository assessmentRecordRepository;
    @Autowired @Qualifier("readinessRecomputeExecutor")
    private ThreadPoolTaskExecutor recomputeExecutor;

    private static final String OWNER_EMAIL = "owner@e.example";
    private static final String OTHER_EMAIL = "other@e.example";
    private static final String[] OPTION_CYCLE = {"A", "B", "C", "D"};

    @BeforeEach
    void seed() {
        // A submit feeds the shared readiness recompute pool, which saves student rows
        // asynchronously; drain it before reseeding so a stale async save cannot collide
        // with the fresh seed rows (duplicate "stu_owner" key).
        await().atMost(Duration.ofSeconds(10)).until(() ->
            recomputeExecutor.getActiveCount() == 0
                && recomputeExecutor.getThreadPoolExecutor().getQueue().isEmpty());

        assessmentRecordRepository.deleteAll();
        attemptQuestionRepository.deleteAll();
        attemptRepository.deleteAll();
        questionRepository.deleteAll();
        studentRepository.deleteAll();

        studentRepository.save(Student.builder()
            .id("stu_owner").campusId("campus_a").name("Owner").email(OWNER_EMAIL)
            .rollNumber("R-1").branch("CSE").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80)
            .placementStatus("ACTIVE").assessmentAptitude(40).build());
        studentRepository.save(Student.builder()
            .id("stu_other").campusId("campus_a").name("Other").email(OTHER_EMAIL)
            .rollNumber("R-2").branch("CSE").graduationYear(2026)
            .cgpa(new BigDecimal("7.5")).backlogs(0).readinessScore(70)
            .placementStatus("ACTIVE").build());

        seedBankQuestions(15);
    }

    @Test
    void listsCatalogWithAvailabilityDrivenByBank() throws Exception {
        String body = mockMvc.perform(get("/api/students/me/aptitude/tests")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andReturn().getResponse().getContentAsString();

        JsonNode tests = objectMapper.readTree(body);
        assertThat(tests.get(0).get("testId").asText()).isEqualTo("QUANTITATIVE");
        assertThat(tests.get(0).get("available").asBoolean()).isTrue();
        assertThat(tests.get(1).get("available").asBoolean()).isFalse();
        assertThat(tests.get(1).get("unavailableReason").asText()).contains("Not enough questions");
        // Mixed grand needs every category, so it is unavailable with a single-category bank.
        assertThat(tests.get(4).get("testId").asText()).isEqualTo("MIXED_GRAND");
        assertThat(tests.get(4).get("available").asBoolean()).isFalse();
    }

    @Test
    void startingOfficialAttemptHidesTheAnswerKey() throws Exception {
        mockMvc.perform(post("/api/students/me/aptitude/attempts")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartAttemptRequest("QUANTITATIVE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("OFFICIAL"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.totalQuestions").value(15))
            .andExpect(jsonPath("$.durationLimitSeconds").value(900))
            .andExpect(jsonPath("$.questions.length()").value(15))
            .andExpect(jsonPath("$.questions[0].prompt").isNotEmpty())
            // No answer key or verdict while the attempt is open.
            .andExpect(jsonPath("$.questions[0].correctOption").isEmpty())
            .andExpect(jsonPath("$.questions[0].explanation").isEmpty())
            .andExpect(jsonPath("$.questions[0].correct").isEmpty());
    }

    @Test
    void startingTwiceResumesTheSameAttempt() throws Exception {
        JsonNode first = startOfficialAttempt();
        JsonNode second = startOfficialAttempt();
        assertThat(second.get("attemptId").asText()).isEqualTo(first.get("attemptId").asText());
    }

    @Test
    void answersPersistSoARefreshResumes() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();
        String questionRowId = started.get("questions").get(0).get("attemptQuestionId").asText();

        mockMvc.perform(put("/api/students/me/aptitude/attempts/" + attemptId + "/answers")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SaveAnswerRequest(questionRowId, "B", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].selectedOption").value("B"))
            .andExpect(jsonPath("$.questions[0].marked").value(true));

        // A fresh GET (page refresh) sees the persisted state.
        mockMvc.perform(get("/api/students/me/aptitude/attempts/" + attemptId)
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].selectedOption").value("B"))
            .andExpect(jsonPath("$.questions[0].marked").value(true));
    }

    @Test
    void submitScoresDeterministicallyAndRevealsTheAnswerKey() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();

        // One right answer, one wrong, thirteen unanswered -> deterministic 1/15.
        answer(attemptId, 1, correctOptionFor(attemptId, 1), false);
        answer(attemptId, 2, wrongOptionFor(attemptId, 2), false);

        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/submit")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(120))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.correctCount").value(1))
            .andExpect(jsonPath("$.score", closeTo(6.67, 0.01)))
            .andExpect(jsonPath("$.aptitudeBefore").value(40))
            .andExpect(jsonPath("$.sections[0].category").value("QUANTITATIVE"))
            .andExpect(jsonPath("$.sections[0].total").value(15))
            .andExpect(jsonPath("$.sections[0].correct").value(1))
            // Answer key and per-question verdicts appear only after submission.
            .andExpect(jsonPath("$.questions[0].correctOption").isNotEmpty())
            .andExpect(jsonPath("$.questions[0].explanation").isNotEmpty())
            .andExpect(jsonPath("$.questions[0].correct").value(true))
            .andExpect(jsonPath("$.questions[1].correct").value(false));

        // History lists the completed attempt first.
        mockMvc.perform(get("/api/students/me/aptitude/history")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].attemptId").value(attemptId))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void officialSubmitFeedsTheAssessmentBridge() {
        JsonNode started;
        try {
            started = startOfficialAttempt();
            String attemptId = started.get("attemptId").asText();
            answer(attemptId, 1, correctOptionFor(attemptId, 1), false);
            mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/submit")
                    .with(authentication(asStudent(OWNER_EMAIL)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(60))))
                .andExpect(status().isOk());
        } catch (Exception exception) {
            throw new AssertionError("Official attempt failed", exception);
        }

        // The bridge runs async after commit; the APTITUDE row and the rebuilt snapshot appear
        // shortly. 1/15 correct -> score 6.67 -> normalized 6.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(assessmentRecordRepository.findByStudentId("stu_owner"))
                .anyMatch(record -> record.getAssessmentType() == AssessmentType.APTITUDE
                    && "Quantitative Aptitude".equals(record.getAssessmentName())
                    && "APTITUDE_TEST".equals(record.getSource()));
            Student reloaded = studentRepository.findById("stu_owner").orElseThrow();
            assertThat(reloaded.getAssessmentAptitude()).isEqualTo(6);
        });
    }

    @Test
    void practiceAttemptNeverFeedsReadiness() throws Exception {
        JsonNode started = mockMvc.perform(post("/api/students/me/aptitude/practice")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartPracticeRequest("QUANTITATIVE", 5))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("PRACTICE"))
            .andExpect(jsonPath("$.testTitle").value("AI Practice"))
            .andExpect(jsonPath("$.totalQuestions").value(5))
            .andExpect(jsonPath("$.durationLimitSeconds").value(300))
            .andReturn().getResponse().getContentAsString() instanceof String body
                ? objectMapper.readTree(body) : null;

        // Practice questions persist privately to the student, never into the bank pool.
        assertThat(questionRepository.findAll())
            .anyMatch(question -> question.getSource() == AptitudeQuestionSource.AI_GENERATED
                && "stu_owner".equals(question.getStudentId()));

        String attemptId = started.get("attemptId").asText();
        for (int ordinal = 1; ordinal <= 5; ordinal++) {
            answer(attemptId, ordinal, correctOptionFor(attemptId, ordinal), false);
        }

        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/submit")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(200))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correctCount").value(5))
            .andExpect(jsonPath("$.score", closeTo(100.0, 0.01)));

        // The practice lane publishes no completion event, so nothing reaches assessment_records
        // and the student's stored aptitude snapshot stays untouched — no async wait needed.
        assertThat(assessmentRecordRepository.findByStudentId("stu_owner")).isEmpty();
        assertThat(studentRepository.findById("stu_owner").orElseThrow().getAssessmentAptitude())
            .isEqualTo(40);
    }

    @Test
    void resubmittingACompletedAttemptIsRejected() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();
        submitExpecting(attemptId, status().isOk());
        submitExpecting(attemptId, status().isConflict());
    }

    @Test
    void anotherStudentCannotSeeOrTouchSomeoneElsesAttempt() throws Exception {
        String attemptId = startOfficialAttempt().get("attemptId").asText();

        mockMvc.perform(get("/api/students/me/aptitude/attempts/" + attemptId)
                .with(authentication(asStudent(OTHER_EMAIL))))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/students/me/aptitude/attempts/" + attemptId + "/answers")
                .with(authentication(asStudent(OTHER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attemptQuestionId\":\"anything\",\"selectedOption\":\"A\",\"marked\":false}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void startingATestWithoutEnoughBankQuestionsIsAConflict() throws Exception {
        mockMvc.perform(post("/api/students/me/aptitude/attempts")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartAttemptRequest("LOGICAL"))))
            .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/students/me/aptitude/tests"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void validationRejectsMissingTestId() throws Exception {
        mockMvc.perform(post("/api/students/me/aptitude/attempts")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ retry + study plan

    @Test
    void retryReopensOnlyTheMissedQuestionsAsPractice() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();
        // One right, one wrong, thirteen skipped -> fourteen missed questions to retry.
        answer(attemptId, 1, correctOptionFor(attemptId, 1), false);
        answer(attemptId, 2, wrongOptionFor(attemptId, 2), false);
        submitExpecting(attemptId, status().isOk());
        String correctQuestionId = rowAt(attemptId, 1).getQuestionId();

        JsonNode retry = objectMapper.readTree(mockMvc
            .perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("PRACTICE"))
            .andExpect(jsonPath("$.testTitle").value("Retry: Missed Questions"))
            .andExpect(jsonPath("$.totalQuestions").value(14))
            .andExpect(jsonPath("$.durationLimitSeconds").value(14 * 60))
            .andReturn().getResponse().getContentAsString());

        // The retry re-draws the missed rows only — the one correct answer stays behind.
        String retryId = retry.get("attemptId").asText();
        assertThat(attemptQuestionRepository.findByAttemptIdOrderByOrdinalAsc(retryId))
            .hasSize(14)
            .noneMatch(row -> row.getQuestionId().equals(correctQuestionId));
        assertThat(attemptRepository.findById(retryId).orElseThrow().getSourceAttemptId())
            .isEqualTo(attemptId);
    }

    @Test
    void retrySubmitNeverFeedsReadiness() throws Exception {
        JsonNode source = startPracticeAttempt(5);
        String attemptId = source.get("attemptId").asText();
        answer(attemptId, 1, correctOptionFor(attemptId, 1), false);
        submitExpecting(attemptId, status().isOk());

        JsonNode retry = objectMapper.readTree(mockMvc
            .perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalQuestions").value(4))
            .andReturn().getResponse().getContentAsString());
        String retryId = retry.get("attemptId").asText();
        for (int ordinal = 1; ordinal <= 4; ordinal++) {
            answer(retryId, ordinal, correctOptionFor(retryId, ordinal), false);
        }
        submitExpecting(retryId, status().isOk());

        // Retries live on the practice lane: no event, no assessment row, no snapshot move.
        assertThat(assessmentRecordRepository.findByStudentId("stu_owner")).isEmpty();
        assertThat(studentRepository.findById("stu_owner").orElseThrow().getAssessmentAptitude())
            .isEqualTo(40);
    }

    @Test
    void retryWithoutMissedQuestionsIsAConflict() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();
        for (int ordinal = 1; ordinal <= 15; ordinal++) {
            answer(attemptId, ordinal, correctOptionFor(attemptId, ordinal), false);
        }
        submitExpecting(attemptId, status().isOk());

        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isConflict());
    }

    @Test
    void retryingTwiceResumesTheSameRetryAttempt() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();
        answer(attemptId, 1, wrongOptionFor(attemptId, 1), false);
        submitExpecting(attemptId, status().isOk());

        JsonNode first = objectMapper.readTree(mockMvc
            .perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(mockMvc
            .perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        assertThat(second.get("attemptId").asText()).isEqualTo(first.get("attemptId").asText());
    }

    @Test
    void studyPlanListsOnlyWeakSectionsInAscendingOrder() throws Exception {
        seedBankQuestions("LOGICAL", "apq_test_l", 15);
        seedBankQuestions("VERBAL", "apq_test_v", 15);
        seedBankQuestions("DATA_INTERPRETATION", "apq_test_d", 15);

        String attemptId = startMixedAttempt().get("attemptId").asText();

        // Section hit rates: QUANTITATIVE 3/10, LOGICAL 6/10, VERBAL 8/10, DI 0/10 — the plan
        // must flag everything below 70% in ascending order of section score.
        Map<String, Integer> targets = Map.of(
            "QUANTITATIVE", 3, "LOGICAL", 6, "VERBAL", 8, "DATA_INTERPRETATION", 0);
        Map<String, Integer> answered = new HashMap<>();
        for (int ordinal = 1; ordinal <= 40; ordinal++) {
            String category = questionRepository
                .findById(rowAt(attemptId, ordinal).getQuestionId())
                .orElseThrow().getCategory();
            int soFar = answered.getOrDefault(category, 0);
            String option = soFar < targets.get(category)
                ? correctOptionFor(attemptId, ordinal)
                : wrongOptionFor(attemptId, ordinal);
            answer(attemptId, ordinal, option, false);
            answered.put(category, soFar + 1);
        }
        submitExpecting(attemptId, status().isOk());

        mockMvc.perform(get("/api/students/me/aptitude/attempts/" + attemptId + "/study-plan")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attemptId").value(attemptId))
            .andExpect(jsonPath("$.recommendations.length()").value(3))
            // Ascending by section score; VERBAL at 80% stays out of the plan.
            .andExpect(jsonPath("$.recommendations[0].category").value("DATA_INTERPRETATION"))
            .andExpect(jsonPath("$.recommendations[0].percent", closeTo(0.0, 0.01)))
            .andExpect(jsonPath("$.recommendations[0].priority").value("HIGH"))
            .andExpect(jsonPath("$.recommendations[1].category").value("QUANTITATIVE"))
            .andExpect(jsonPath("$.recommendations[1].percent", closeTo(30.0, 0.01)))
            .andExpect(jsonPath("$.recommendations[1].priority").value("HIGH"))
            .andExpect(jsonPath("$.recommendations[2].category").value("LOGICAL"))
            .andExpect(jsonPath("$.recommendations[2].percent", closeTo(60.0, 0.01)))
            .andExpect(jsonPath("$.recommendations[2].priority").value("MEDIUM"))
            // Curated content is grounded: topics, resources and a practice task all present.
            .andExpect(jsonPath("$.recommendations[0].topics").isNotEmpty())
            .andExpect(jsonPath("$.recommendations[0].resources[0].url").isNotEmpty())
            .andExpect(jsonPath("$.recommendations[0].practiceTask").isNotEmpty());
    }

    @Test
    void studyPlanAndRetryAreRejectedWhileTheAttemptIsInProgress() throws Exception {
        String attemptId = startOfficialAttempt().get("attemptId").asText();

        mockMvc.perform(get("/api/students/me/aptitude/attempts/" + attemptId + "/study-plan")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isConflict());

        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OWNER_EMAIL))))
            .andExpect(status().isConflict());
    }

    @Test
    void anotherStudentCannotRetrySomeoneElsesAttempt() throws Exception {
        JsonNode started = startOfficialAttempt();
        String attemptId = started.get("attemptId").asText();
        answer(attemptId, 1, wrongOptionFor(attemptId, 1), false);
        submitExpecting(attemptId, status().isOk());

        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/retry")
                .with(authentication(asStudent(OTHER_EMAIL))))
            .andExpect(status().isNotFound());
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

    private void seedBankQuestions(String category, String idPrefix, int count) {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 1; i <= count; i++) {
            questionRepository.save(AptitudeQuestion.builder()
                .id(idPrefix + i)
                .source(AptitudeQuestionSource.SEEDED)
                .category(category)
                .difficulty("EASY")
                .prompt(category + " question " + i)
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

    private JsonNode startOfficialAttempt() throws Exception {
        String body = mockMvc.perform(post("/api/students/me/aptitude/attempts")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartAttemptRequest("QUANTITATIVE"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode startMixedAttempt() throws Exception {
        String body = mockMvc.perform(post("/api/students/me/aptitude/attempts")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StartAttemptRequest("MIXED_GRAND"))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode startPracticeAttempt(int count) throws Exception {
        String body = mockMvc.perform(post("/api/students/me/aptitude/practice")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new StartPracticeRequest("QUANTITATIVE", count))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void answer(String attemptId, int ordinal, String option, boolean marked) throws Exception {
        mockMvc.perform(put("/api/students/me/aptitude/attempts/" + attemptId + "/answers")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new SaveAnswerRequest(attemptQuestionIdAt(attemptId, ordinal), option, marked))))
            .andExpect(status().isOk());
    }

    private void submitExpecting(String attemptId, org.springframework.test.web.servlet.ResultMatcher expected)
        throws Exception {
        mockMvc.perform(post("/api/students/me/aptitude/attempts/" + attemptId + "/submit")
                .with(authentication(asStudent(OWNER_EMAIL)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SubmitAttemptRequest(60))))
            .andExpect(expected);
    }

    private String attemptQuestionIdAt(String attemptId, int ordinal) {
        return rowAt(attemptId, ordinal).getId();
    }

    /** The correct option is read from the repository — the HTTP response hides it until submit. */
    private String correctOptionFor(String attemptId, int ordinal) {
        String questionId = rowAt(attemptId, ordinal).getQuestionId();
        return questionRepository.findById(questionId).orElseThrow().getCorrectOption();
    }

    private String wrongOptionFor(String attemptId, int ordinal) {
        String correct = correctOptionFor(attemptId, ordinal);
        return "A".equals(correct) ? "B" : "A";
    }

    private com.nexploy.campuslink.aptitude.entity.AptitudeAttemptQuestion rowAt(
        String attemptId, int ordinal) {
        return attemptQuestionRepository.findByAttemptIdOrderByOrdinalAsc(attemptId).stream()
            .filter(row -> row.getOrdinal() == ordinal)
            .findFirst()
            .orElseThrow();
    }

    private Authentication asStudent(String email) {
        UserAccount user = UserAccount.builder()
            .username(email).password("{noop}secret").campusId("campus_a")
            .role(UserRole.STUDENT).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}