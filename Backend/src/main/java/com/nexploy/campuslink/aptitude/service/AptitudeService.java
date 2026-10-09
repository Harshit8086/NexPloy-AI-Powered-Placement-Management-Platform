package com.nexploy.campuslink.aptitude.service;

import com.nexploy.campuslink.aptitude.ai.AptitudeAi;
import com.nexploy.campuslink.aptitude.ai.AptitudeAnalysis;
import com.nexploy.campuslink.aptitude.ai.AptitudeAttemptStats;
import com.nexploy.campuslink.aptitude.ai.AptitudeQuestionDraft;
import com.nexploy.campuslink.aptitude.dto.AptitudeAnalysisResponse;
import com.nexploy.campuslink.aptitude.dto.AptitudeTestResponse;
import com.nexploy.campuslink.aptitude.dto.AttemptQuestionResponse;
import com.nexploy.campuslink.aptitude.dto.AttemptResponse;
import com.nexploy.campuslink.aptitude.dto.AttemptSummaryResponse;
import com.nexploy.campuslink.aptitude.dto.SaveAnswerRequest;
import com.nexploy.campuslink.aptitude.dto.StudyPlanResponse;
import com.nexploy.campuslink.aptitude.entity.AptitudeAttempt;
import com.nexploy.campuslink.aptitude.entity.AptitudeAttemptMode;
import com.nexploy.campuslink.aptitude.entity.AptitudeAttemptQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeAttemptStatus;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestionSource;
import com.nexploy.campuslink.aptitude.event.AptitudeAttemptCompletedEvent;
import com.nexploy.campuslink.aptitude.exception.AptitudeAttemptNotFoundException;
import com.nexploy.campuslink.aptitude.exception.AptitudeTestNotFoundException;
import com.nexploy.campuslink.aptitude.exception.InsufficientAptitudeBankException;
import com.nexploy.campuslink.aptitude.exception.InvalidAptitudeAttemptStateException;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptQuestionRepository;
import com.nexploy.campuslink.aptitude.repository.AptitudeAttemptRepository;
import com.nexploy.campuslink.aptitude.repository.AptitudeQuestionRepository;
import com.nexploy.campuslink.student.entity.Student;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The student-facing side of the aptitude module: the two-lane hybrid.
 *
 * <p><b>Official lane</b> — timed MCQ tests drawn from the curated bank (seeded + officer-added),
 * scored deterministically with no AI. A completed official attempt publishes {@link
 * AptitudeAttemptCompletedEvent}, which the optional assessment bridge folds into readiness.
 *
 * <p><b>Practice lane</b> — AI-generated questions persisted privately to the student. It never
 * publishes the completion event, so practice scores can never reach placement readiness (an AI
 * answer key may be wrong).
 *
 * <p><b>Retry flow</b> — a completed attempt's missed questions can be re-attempted as a
 * practice-lane attempt that re-draws the same question rows, inheriting the practice lane's
 * no-event guarantee; retries never move readiness either.
 *
 * <p>Ownership is enforced on every read: an attempt that belongs to another student reads as 404,
 * exactly like a missing one, so attempt ids cannot be probed across accounts.
 */
@Service
@RequiredArgsConstructor
public class AptitudeService {

    /** The two sources allowed in an official draw — AI-generated questions never leak into tests. */
    private static final List<AptitudeQuestionSource> BANK_SOURCES =
        List.of(AptitudeQuestionSource.SEEDED, AptitudeQuestionSource.OFFICER);

    /** Grace added to the client-reported timer before clamping (timer runs in the browser). */
    private static final int TIMER_GRACE_SECONDS = 60;
    private static final int PRACTICE_SECONDS_PER_QUESTION = 60;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Section scores below this percent count as weak and earn a study recommendation. */
    private static final BigDecimal WEAK_SECTION_THRESHOLD = BigDecimal.valueOf(70);

    /** Weak sections scoring below this percent are escalated to HIGH priority. */
    private static final BigDecimal HIGH_PRIORITY_THRESHOLD = BigDecimal.valueOf(50);

    private final AptitudeQuestionRepository questionRepository;
    private final AptitudeAttemptRepository attemptRepository;
    private final AptitudeAttemptQuestionRepository attemptQuestionRepository;
    private final AptitudeTestCatalog catalog;
    private final AptitudeAi aptitudeAi;
    private final AptitudeStudyCatalog studyCatalog;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------ catalog

    /** Catalog tests with bank-derived availability and the student's latest official score. */
    @Transactional(readOnly = true)
    public List<AptitudeTestResponse> listTests(Student student) {
        List<AptitudeTestResponse> tests = new ArrayList<>();
        for (AptitudeTestCatalog.TestDefinition test : catalog.all()) {
            boolean available = true;
            for (Map.Entry<String, Integer> required : catalog.requiredByCategory(test).entrySet()) {
                long banked = questionRepository.countOfficialBank(
                    BANK_SOURCES, student.getCampusId(), required.getKey());
                if (banked < required.getValue()) {
                    available = false;
                    break;
                }
            }
            BigDecimal lastScore = attemptRepository
                .findFirstByStudentIdAndTestIdAndModeAndStatusOrderByStartedAtDesc(
                    student.getId(), test.testId(), AptitudeAttemptMode.OFFICIAL,
                    AptitudeAttemptStatus.COMPLETED)
                .map(AptitudeAttempt::getScore)
                .orElse(null);
            tests.add(new AptitudeTestResponse(
                test.testId(), test.title(), test.category(), test.questionCount(),
                test.durationSeconds(), test.description(), available,
                available ? null : "Not enough questions in the bank yet.",
                lastScore));
        }
        return List.copyOf(tests);
    }

    // ------------------------------------------------------------------ official lane

    /**
     * Starts an official attempt, or resumes the one already in progress for this test (no
     * duplicate attempts while one is open).
     */
    @Transactional
    public AttemptResponse startOfficialAttempt(Student student, String testId) {
        AptitudeTestCatalog.TestDefinition test = catalog.byId(testId)
            .orElseThrow(() -> new AptitudeTestNotFoundException("Unknown aptitude test: " + testId));

        return attemptRepository
            .findFirstByStudentIdAndTestIdAndStatus(
                student.getId(), testId, AptitudeAttemptStatus.IN_PROGRESS)
            .map(this::serialize)
            .orElseGet(() -> createOfficialAttempt(student, test));
    }

    private AttemptResponse createOfficialAttempt(Student student, AptitudeTestCatalog.TestDefinition test) {
        List<AptitudeQuestion> drawn = new ArrayList<>();
        for (Map.Entry<String, Integer> required : catalog.requiredByCategory(test).entrySet()) {
            List<AptitudeQuestion> bank = questionRepository.findOfficialBank(
                BANK_SOURCES, student.getCampusId(), required.getKey());
            if (bank.size() < required.getValue()) {
                throw new InsufficientAptitudeBankException(
                    "Not enough questions in the bank yet for this test.");
            }
            Collections.shuffle(bank);
            drawn.addAll(bank.subList(0, required.getValue()));
        }
        Collections.shuffle(drawn);

        AptitudeAttempt attempt = saveAttempt(student, test.testId(), AptitudeAttemptMode.OFFICIAL,
            test.category(), test.questionCount(), test.durationSeconds());
        saveAttemptQuestions(attempt, drawn);
        return serialize(attempt);
    }

    // ------------------------------------------------------------------ practice lane

    /**
     * Starts an AI practice attempt. The generated questions are persisted privately to the
     * student (source AI_GENERATED) so attempt history stays reviewable, but they are never
     * eligible for an official draw and never feed readiness.
     */
    @Transactional
    public AttemptResponse startPracticeAttempt(Student student, String category, int count) {
        String normalized = category == null ? "" : category.trim().toUpperCase(java.util.Locale.ROOT);
        if (!AptitudeTestCatalog.CATEGORIES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown practice category: " + category);
        }
        List<AptitudeQuestionDraft> drafts = aptitudeAi.generatePracticeQuestions(normalized, count);

        OffsetDateTime now = OffsetDateTime.now();
        List<AptitudeQuestion> questions = new ArrayList<>();
        for (AptitudeQuestionDraft draft : drafts) {
            questions.add(questionRepository.save(AptitudeQuestion.builder()
                .id(UUID.randomUUID().toString())
                .campusId(student.getCampusId())
                .studentId(student.getId())
                .source(AptitudeQuestionSource.AI_GENERATED)
                .category(normalized)
                .prompt(draft.prompt())
                .optionA(draft.optionA())
                .optionB(draft.optionB())
                .optionC(draft.optionC())
                .optionD(draft.optionD())
                .correctOption(draft.correctOption())
                .explanation(draft.explanation())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build()));
        }

        AptitudeAttempt attempt = saveAttempt(student, AptitudeTestCatalog.PRACTICE_TEST_ID,
            AptitudeAttemptMode.PRACTICE, normalized, questions.size(),
            questions.size() * PRACTICE_SECONDS_PER_QUESTION);
        saveAttemptQuestions(attempt, questions);
        return serialize(attempt);
    }

    // ------------------------------------------------------------------ shared attempt operations

    /** Ownership-checked attempt view; cross-student access reads as 404. */
    @Transactional(readOnly = true)
    public AttemptResponse getAttempt(Student student, String attemptId) {
        return serialize(ownedAttempt(student, attemptId));
    }

    /**
     * Persists one question's selection/mark state so a page refresh resumes exactly where the
     * student left off. A completed attempt rejects edits with 409.
     */
    @Transactional
    public AttemptResponse saveAnswer(Student student, String attemptId, SaveAnswerRequest request) {
        AptitudeAttempt attempt = ownedAttempt(student, attemptId);
        requireInProgress(attempt);

        AptitudeAttemptQuestion row = attemptQuestionRepository
            .findByIdAndAttemptId(request.attemptQuestionId(), attempt.getId())
            .orElseThrow(() -> new AptitudeAttemptNotFoundException("Attempt question not found."));
        row.setSelectedOption(request.selectedOption());
        row.setMarked(request.marked());
        attemptQuestionRepository.save(row);
        return serialize(attempt);
    }

    /**
     * Submits the attempt with deterministic scoring: each selected option is compared against the
     * question's correct option — no AI anywhere on this path. Publishes the readiness-feed event
     * for OFFICIAL attempts only.
     */
    @Transactional
    public AttemptResponse submitAttempt(Student student, String attemptId, int reportedSeconds) {
        AptitudeAttempt attempt = ownedAttempt(student, attemptId);
        requireInProgress(attempt);

        List<AptitudeAttemptQuestion> rows = attemptQuestionRepository
            .findByAttemptIdOrderByOrdinalAsc(attempt.getId());
        Map<String, AptitudeQuestion> questions = questionsById(rows);

        int correctCount = 0;
        for (AptitudeAttemptQuestion row : rows) {
            AptitudeQuestion question = questions.get(row.getQuestionId());
            boolean correct = question != null
                && row.getSelectedOption() != null
                && row.getSelectedOption().equals(question.getCorrectOption());
            row.setCorrect(correct);
            if (correct) {
                correctCount++;
            }
        }
        attemptQuestionRepository.saveAll(rows);

        BigDecimal score = percent(correctCount, attempt.getTotalQuestions());
        OffsetDateTime now = OffsetDateTime.now();
        attempt.setStatus(AptitudeAttemptStatus.COMPLETED);
        attempt.setCorrectCount(correctCount);
        attempt.setScore(score);
        attempt.setTimeSpentSeconds(Math.max(0,
            Math.min(reportedSeconds, attempt.getDurationLimitSeconds() + TIMER_GRACE_SECONDS)));
        Integer aptitudeBefore = student.getAssessmentAptitude();
        attempt.setAptitudeBefore(aptitudeBefore == null ? null : BigDecimal.valueOf(aptitudeBefore));
        attempt.setSubmittedAt(now);
        attemptRepository.save(attempt);

        // Only the official lane reaches readiness; the practice lane publishes nothing, so an AI
        // answer key can never feed the assessment bridge. When the bridge is disabled the event
        // has no listener and is a no-op.
        if (attempt.getMode() == AptitudeAttemptMode.OFFICIAL) {
            eventPublisher.publishEvent(new AptitudeAttemptCompletedEvent(
                student.getId(), student.getCampusId(), attempt.getId(), attempt.getTestId(),
                score, now));
        }
        return serialize(attempt);
    }

    /** AI narration of a completed attempt, plus the before/after aptitude snapshot. */
    @Transactional(readOnly = true)
    public AptitudeAnalysisResponse analyze(Student student, String attemptId) {
        AptitudeAttempt attempt = ownedAttempt(student, attemptId);
        if (attempt.getStatus() != AptitudeAttemptStatus.COMPLETED) {
            throw new InvalidAptitudeAttemptStateException(
                "Analysis is available only after the attempt is submitted.");
        }

        List<AptitudeAttemptQuestion> rows = attemptQuestionRepository
            .findByAttemptIdOrderByOrdinalAsc(attempt.getId());
        Map<String, AptitudeQuestion> questions = questionsById(rows);

        // The AI only ever sees deterministic totals — never raw questions or answers — so there is
        // no student free text to screen here and no content the model could leak back.
        List<AptitudeAttemptStats.CategoryStats> sections = sectionTotals(rows, questions).entrySet().stream()
            .map(entry -> new AptitudeAttemptStats.CategoryStats(
                entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
            .toList();
        AptitudeAttemptStats stats = new AptitudeAttemptStats(
            testTitle(attempt),
            attempt.getTotalQuestions(),
            attempt.getCorrectCount() == null ? 0 : attempt.getCorrectCount(),
            attempt.getTimeSpentSeconds() == null ? 0 : attempt.getTimeSpentSeconds(),
            attempt.getDurationLimitSeconds(),
            sections);
        AptitudeAnalysis analysis = aptitudeAi.analyze(stats);

        Integer current = student.getAssessmentAptitude();
        return new AptitudeAnalysisResponse(
            attempt.getId(), testTitle(attempt), attempt.getScore(),
            analysis.summary(), analysis.strengths(), analysis.weaknesses(), analysis.tips(),
            attempt.getAptitudeBefore(),
            current == null ? null : BigDecimal.valueOf(current));
    }

    /**
     * Deterministic study plan for a completed attempt: one curated recommendation per weak section
     * (below the weak threshold), weakest first, grounded in {@link AptitudeStudyCatalog}. No AI is
     * involved — an uncatalogued section is skipped, never fabricated.
     */
    @Transactional(readOnly = true)
    public StudyPlanResponse studyPlan(Student student, String attemptId) {
        AptitudeAttempt attempt = ownedAttempt(student, attemptId);
        if (attempt.getStatus() != AptitudeAttemptStatus.COMPLETED) {
            throw new InvalidAptitudeAttemptStateException(
                "A study plan is available only after the attempt is submitted.");
        }

        List<AptitudeAttemptQuestion> rows = attemptQuestionRepository
            .findByAttemptIdOrderByOrdinalAsc(attempt.getId());
        Map<String, AptitudeQuestion> questions = questionsById(rows);

        List<StudyPlanResponse.Recommendation> recommendations = new ArrayList<>();
        sectionTotals(rows, questions).entrySet().stream()
            .map(section -> Map.entry(section.getKey(),
                percent(section.getValue()[1], section.getValue()[0])))
            .filter(section -> section.getValue().compareTo(WEAK_SECTION_THRESHOLD) < 0)
            .sorted(Map.Entry.comparingByValue())
            .forEach(section -> studyCatalog.entryFor(section.getKey()).ifPresent(entry ->
                recommendations.add(new StudyPlanResponse.Recommendation(
                    section.getKey(), section.getValue(),
                    section.getValue().compareTo(HIGH_PRIORITY_THRESHOLD) < 0 ? "HIGH" : "MEDIUM",
                    entry.topics(),
                    entry.resources().stream()
                        .map(resource -> new StudyPlanResponse.Resource(
                            resource.title(), resource.provider(), resource.url(), resource.type()))
                        .toList(),
                    entry.practiceTask()))));
        return new StudyPlanResponse(attempt.getId(), List.copyOf(recommendations));
    }

    // ------------------------------------------------------------------ retry lane

    /**
     * Starts a practice-lane retry of a completed attempt's missed questions (wrong + skipped), or
     * resumes the retry already in progress for that source. A retry re-draws the same question
     * rows — no new questions, no AI — and inherits the practice lane's no-event guarantee, so it
     * can never move readiness.
     */
    @Transactional
    public AttemptResponse startRetryAttempt(Student student, String attemptId) {
        AptitudeAttempt source = ownedAttempt(student, attemptId);
        if (source.getStatus() != AptitudeAttemptStatus.COMPLETED) {
            throw new InvalidAptitudeAttemptStateException(
                "Questions can be retried only after the attempt is submitted.");
        }

        return attemptRepository
            .findFirstByStudentIdAndSourceAttemptIdAndStatus(
                student.getId(), source.getId(), AptitudeAttemptStatus.IN_PROGRESS)
            .map(this::serialize)
            .orElseGet(() -> createRetryAttempt(student, source));
    }

    private AttemptResponse createRetryAttempt(Student student, AptitudeAttempt source) {
        List<AptitudeAttemptQuestion> rows = attemptQuestionRepository
            .findByAttemptIdOrderByOrdinalAsc(source.getId());
        Map<String, AptitudeQuestion> questions = questionsById(rows);
        List<AptitudeQuestion> missed = rows.stream()
            .filter(row -> !Boolean.TRUE.equals(row.getCorrect()))
            .map(row -> questions.get(row.getQuestionId()))
            .filter(Objects::nonNull)
            .toList();
        if (missed.isEmpty()) {
            throw new InvalidAptitudeAttemptStateException("No missed questions to retry.");
        }

        AptitudeAttempt attempt = saveAttempt(student, AptitudeTestCatalog.RETRY_TEST_ID,
            AptitudeAttemptMode.PRACTICE, source.getCategory(), missed.size(),
            missed.size() * PRACTICE_SECONDS_PER_QUESTION, source.getId());
        saveAttemptQuestions(attempt, missed);
        return serialize(attempt);
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> history(Student student) {
        return attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId()).stream()
            .map(attempt -> new AttemptSummaryResponse(
                attempt.getId(), attempt.getTestId(), testTitle(attempt),
                attempt.getMode().name(), attempt.getCategory(), attempt.getStatus().name(),
                attempt.getTotalQuestions(), attempt.getCorrectCount(), attempt.getScore(),
                attempt.getStartedAt(), attempt.getSubmittedAt()))
            .toList();
    }

    // ------------------------------------------------------------------ internals

    private AptitudeAttempt ownedAttempt(Student student, String attemptId) {
        return attemptRepository.findByIdAndStudentId(attemptId, student.getId())
            .orElseThrow(() -> new AptitudeAttemptNotFoundException("Attempt not found."));
    }

    private static void requireInProgress(AptitudeAttempt attempt) {
        if (attempt.getStatus() != AptitudeAttemptStatus.IN_PROGRESS) {
            throw new InvalidAptitudeAttemptStateException("This attempt has already been submitted.");
        }
    }

    private AptitudeAttempt saveAttempt(Student student, String testId, AptitudeAttemptMode mode,
                                        String category, int totalQuestions, int durationSeconds) {
        return saveAttempt(student, testId, mode, category, totalQuestions, durationSeconds, null);
    }

    /** {@code sourceAttemptId} links a retry attempt to the attempt it re-draws missed questions from. */
    private AptitudeAttempt saveAttempt(Student student, String testId, AptitudeAttemptMode mode,
                                        String category, int totalQuestions, int durationSeconds,
                                        String sourceAttemptId) {
        return attemptRepository.save(AptitudeAttempt.builder()
            .id(UUID.randomUUID().toString())
            .studentId(student.getId())
            .testId(testId)
            .mode(mode)
            .category(category)
            .status(AptitudeAttemptStatus.IN_PROGRESS)
            .totalQuestions(totalQuestions)
            .durationLimitSeconds(durationSeconds)
            .sourceAttemptId(sourceAttemptId)
            .startedAt(OffsetDateTime.now())
            .build());
    }

    private void saveAttemptQuestions(AptitudeAttempt attempt, List<AptitudeQuestion> questions) {
        List<AptitudeAttemptQuestion> rows = new ArrayList<>();
        int ordinal = 1;
        for (AptitudeQuestion question : questions) {
            rows.add(AptitudeAttemptQuestion.builder()
                .id(UUID.randomUUID().toString())
                .attemptId(attempt.getId())
                .questionId(question.getId())
                .ordinal(ordinal++)
                .marked(false)
                .build());
        }
        attemptQuestionRepository.saveAll(rows);
    }

    private AttemptResponse serialize(AptitudeAttempt attempt) {
        List<AptitudeAttemptQuestion> rows = attemptQuestionRepository
            .findByAttemptIdOrderByOrdinalAsc(attempt.getId());
        Map<String, AptitudeQuestion> questions = questionsById(rows);
        boolean completed = attempt.getStatus() == AptitudeAttemptStatus.COMPLETED;

        List<AttemptQuestionResponse> questionResponses = rows.stream()
            .map(row -> {
                AptitudeQuestion question = questions.get(row.getQuestionId());
                return new AttemptQuestionResponse(
                    row.getId(), row.getOrdinal(),
                    question == null ? null : question.getCategory(),
                    question == null ? null : question.getPrompt(),
                    question == null ? null : question.getOptionA(),
                    question == null ? null : question.getOptionB(),
                    question == null ? null : question.getOptionC(),
                    question == null ? null : question.getOptionD(),
                    row.getSelectedOption(), row.isMarked(), row.getCorrect(),
                    // Answer key stays hidden until the attempt is completed, so an in-progress
                    // attempt can never leak the correct option or explanation.
                    completed && question != null ? question.getCorrectOption() : null,
                    completed && question != null ? question.getExplanation() : null);
            })
            .toList();

        List<AttemptResponse.SectionResult> sections = completed
            ? sectionTotals(rows, questions).entrySet().stream()
                .map(entry -> new AttemptResponse.SectionResult(
                    entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                    percent(entry.getValue()[1], entry.getValue()[0])))
                .toList()
            : null;

        return new AttemptResponse(
            attempt.getId(), attempt.getTestId(), testTitle(attempt),
            attempt.getMode().name(), attempt.getCategory(), attempt.getStatus().name(),
            attempt.getTotalQuestions(), attempt.getDurationLimitSeconds(),
            attempt.getTimeSpentSeconds(), attempt.getScore(), attempt.getCorrectCount(),
            attempt.getAptitudeBefore(), attempt.getStartedAt(), attempt.getSubmittedAt(),
            questionResponses, sections);
    }

    private Map<String, AptitudeQuestion> questionsById(List<AptitudeAttemptQuestion> rows) {
        List<String> ids = rows.stream().map(AptitudeAttemptQuestion::getQuestionId).toList();
        return questionRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(AptitudeQuestion::getId, Function.identity()));
    }

    /** Category → {total, correct}, in catalog display order. */
    private Map<String, int[]> sectionTotals(List<AptitudeAttemptQuestion> rows,
                                             Map<String, AptitudeQuestion> questions) {
        Map<String, int[]> byCategory = new LinkedHashMap<>();
        for (AptitudeAttemptQuestion row : rows) {
            AptitudeQuestion question = questions.get(row.getQuestionId());
            String category = question == null ? "UNKNOWN" : question.getCategory();
            int[] totals = byCategory.computeIfAbsent(category, key -> new int[2]);
            totals[0]++;
            if (Boolean.TRUE.equals(row.getCorrect())) {
                totals[1]++;
            }
        }
        // Re-key into the fixed category order so the result page renders a stable layout.
        Map<String, int[]> ordered = new LinkedHashMap<>();
        for (String category : AptitudeTestCatalog.CATEGORIES) {
            int[] totals = byCategory.remove(category);
            if (totals != null) {
                ordered.put(category, totals);
            }
        }
        ordered.putAll(byCategory);
        return ordered;
    }

    private String testTitle(AptitudeAttempt attempt) {
        if (AptitudeTestCatalog.RETRY_TEST_ID.equals(attempt.getTestId())) {
            return "Retry: Missed Questions";
        }
        if (attempt.getMode() == AptitudeAttemptMode.PRACTICE) {
            return "AI Practice";
        }
        return catalog.byId(attempt.getTestId())
            .map(AptitudeTestCatalog.TestDefinition::title)
            .orElse("Aptitude Test");
    }

    private static BigDecimal percent(int correct, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(correct)
            .multiply(HUNDRED)
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
