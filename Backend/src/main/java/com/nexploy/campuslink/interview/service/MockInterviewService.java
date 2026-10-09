package com.nexploy.campuslink.interview.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.ai.service.PromptInjectionGuard;
import com.nexploy.campuslink.interview.ai.AnswerEvaluation;
import com.nexploy.campuslink.interview.ai.AnsweredQuestion;
import com.nexploy.campuslink.interview.ai.InterviewSummary;
import com.nexploy.campuslink.interview.ai.MockInterviewAi;
import com.nexploy.campuslink.interview.ai.QuestionDraft;
import com.nexploy.campuslink.interview.dto.AnswerFeedback;
import com.nexploy.campuslink.interview.dto.MockInterviewQuestionResponse;
import com.nexploy.campuslink.interview.dto.MockInterviewResponse;
import com.nexploy.campuslink.interview.dto.MockInterviewSummaryResponse;
import com.nexploy.campuslink.interview.dto.StartMockInterviewRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerResponse;
import com.nexploy.campuslink.interview.entity.MockInterview;
import com.nexploy.campuslink.interview.entity.MockInterviewQuestion;
import com.nexploy.campuslink.interview.entity.MockInterviewStatus;
import com.nexploy.campuslink.interview.event.MockInterviewCompletedEvent;
import com.nexploy.campuslink.interview.exception.InvalidMockInterviewStateException;
import com.nexploy.campuslink.interview.exception.MockInterviewNotFoundException;
import com.nexploy.campuslink.interview.repository.MockInterviewQuestionRepository;
import com.nexploy.campuslink.interview.repository.MockInterviewRepository;
import com.nexploy.campuslink.student.entity.Student;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the AI mock (practice) interview lifecycle: start (generate questions), answer (score
 * one answer, advance), and complete (produce the report). Ownership/campus checks live in the
 * controller; this service assumes the {@link Student} passed in is the authenticated owner.
 *
 * <p>All student free-text (the target role, every answer) is screened through {@link
 * PromptInjectionGuard} before it reaches the model, and model output is treated as read-only
 * evidence — it never mutates authorization, eligibility, offers, readiness or the digital twin.
 * This history is descriptive only.
 */
@Service
public class MockInterviewService {

    private static final Logger log = LoggerFactory.getLogger(MockInterviewService.class);

    private final MockInterviewRepository interviewRepository;
    private final MockInterviewQuestionRepository questionRepository;
    private final MockInterviewAi mockInterviewAi;
    private final PromptInjectionGuard promptInjectionGuard;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public MockInterviewService(
        MockInterviewRepository interviewRepository,
        MockInterviewQuestionRepository questionRepository,
        MockInterviewAi mockInterviewAi,
        PromptInjectionGuard promptInjectionGuard,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher) {
        this.interviewRepository = interviewRepository;
        this.questionRepository = questionRepository;
        this.mockInterviewAi = mockInterviewAi;
        this.promptInjectionGuard = promptInjectionGuard;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /** Starts a new practice interview for the student: generates and persists its questions. */
    @Transactional
    public MockInterviewResponse start(Student student, StartMockInterviewRequest request) {
        String role = request.role() == null ? "" : request.role().trim();
        promptInjectionGuard.verify(role);

        String resumeContext = request.useResume() ? buildResumeContext(student) : null;
        List<QuestionDraft> drafts =
            mockInterviewAi.generateQuestions(request.type(), role, request.useResume(), resumeContext);
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalStateException("Failed to generate interview questions");
        }

        OffsetDateTime now = OffsetDateTime.now();
        MockInterview interview = MockInterview.builder()
            .id(UUID.randomUUID().toString())
            .campusId(student.getCampusId())
            .studentId(student.getId())
            .type(request.type())
            .role(role.isBlank() ? null : role)
            .useResume(request.useResume())
            .status(MockInterviewStatus.IN_PROGRESS)
            .currentQuestion(0)
            .createdAt(now)
            .updatedAt(now)
            .build();
        interviewRepository.save(interview);

        List<MockInterviewQuestion> questions = new ArrayList<>();
        int ordinal = 0;
        for (QuestionDraft draft : drafts) {
            questions.add(MockInterviewQuestion.builder()
                .id(UUID.randomUUID().toString())
                .mockInterviewId(interview.getId())
                .ordinal(ordinal++)
                .prompt(draft.question())
                .difficulty(draft.difficulty())
                .timerSeconds(draft.timerSeconds())
                .isCoding(draft.coding())
                .language(draft.language())
                .build());
        }
        questionRepository.saveAll(questions);
        return toResponse(interview, questions);
    }

    /** The student's practice-interview history, newest first. */
    @Transactional(readOnly = true)
    public List<MockInterviewSummaryResponse> list(Student student) {
        return interviewRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
            .map(MockInterviewSummaryResponse::from)
            .toList();
    }

    /** One practice interview in full, verifying the student owns it. */
    @Transactional(readOnly = true)
    public MockInterviewResponse get(Student student, String id) {
        MockInterview interview = loadOwned(student, id);
        return toResponse(interview, orderedQuestions(interview.getId()));
    }

    /** Scores the answer to the current question, advances, and finalises on the last question. */
    @Transactional
    public SubmitAnswerResponse submitAnswer(Student student, String id, SubmitAnswerRequest request) {
        MockInterview interview = loadOwned(student, id);
        if (interview.getStatus() == MockInterviewStatus.COMPLETED) {
            throw new InvalidMockInterviewStateException("Interview already completed: " + id);
        }
        String answer = request.answer() == null ? "" : request.answer();
        promptInjectionGuard.verify(answer);

        List<MockInterviewQuestion> questions = orderedQuestions(interview.getId());
        int index = interview.getCurrentQuestion();
        if (index >= questions.size()) {
            throw new InvalidMockInterviewStateException("No further questions to answer for: " + id);
        }
        MockInterviewQuestion question = questions.get(index);

        AnswerEvaluation evaluation = mockInterviewAi.evaluate(question.getPrompt(), answer);
        question.setStudentAnswer(answer);
        question.setScore(evaluation.score());
        question.setFeedback(writeJson(evaluation));
        question.setAnsweredAt(OffsetDateTime.now());
        questionRepository.save(question);

        interview.setCurrentQuestion(index + 1);
        interview.setUpdatedAt(OffsetDateTime.now());
        boolean completed = interview.getCurrentQuestion() >= questions.size();
        if (completed) {
            finalizeReport(interview, questions);
        }
        interviewRepository.save(interview);

        return new SubmitAnswerResponse(
            question.getOrdinal(),
            AnswerFeedback.from(evaluation),
            interview.getStatus(),
            interview.getCurrentQuestion(),
            questions.size(),
            completed);
    }

    /** Finalises an interview on demand (idempotent): summarises the answered questions and closes it. */
    @Transactional
    public MockInterviewResponse complete(Student student, String id) {
        MockInterview interview = loadOwned(student, id);
        List<MockInterviewQuestion> questions = orderedQuestions(interview.getId());
        if (interview.getStatus() != MockInterviewStatus.COMPLETED) {
            finalizeReport(interview, questions);
            interview.setUpdatedAt(OffsetDateTime.now());
            interviewRepository.save(interview);
        }
        return toResponse(interview, questions);
    }

    // ---- internals ----

    /** Builds the final report from every answered question and marks the interview COMPLETED. */
    private void finalizeReport(MockInterview interview, List<MockInterviewQuestion> questions) {
        List<AnsweredQuestion> answered = questions.stream()
            .filter(q -> q.getScore() != null)
            .map(q -> new AnsweredQuestion(
                q.getPrompt(), q.getStudentAnswer() == null ? "" : q.getStudentAnswer(), q.getScore()))
            .toList();
        InterviewSummary summary =
            mockInterviewAi.summarize(interview.getRole(), interview.getType(), answered);
        interview.setOverallScore(summary.overallScore());
        interview.setSummary(summary.summary());
        interview.setStrengths(writeJson(summary.strengths()));
        interview.setWeaknesses(writeJson(summary.weaknesses()));
        interview.setRecommendations(writeJson(summary.recommendations()));
        interview.setStatus(MockInterviewStatus.COMPLETED);

        // Fired exactly once per completion (this is the single COMPLETED-transition point). Delivered
        // AFTER_COMMIT to the optional, default-off MockInterviewAssessmentBridge; when the bridge is
        // disabled the event has no listener and is a no-op. Scores are advisory evidence only.
        eventPublisher.publishEvent(new MockInterviewCompletedEvent(
            interview.getStudentId(),
            interview.getCampusId(),
            interview.getId(),
            interview.getOverallScore(),
            averageCommunication(questions),
            OffsetDateTime.now()));
    }

    /**
     * Average of the per-answer {@code communication} metric (0-100) across answered questions, or
     * null when no answered question carried a parseable evaluation. The metric is stored inside each
     * question's {@code feedback} JSON ({@link AnswerEvaluation}).
     */
    private Integer averageCommunication(List<MockInterviewQuestion> questions) {
        int sum = 0;
        int count = 0;
        for (MockInterviewQuestion q : questions) {
            if (q.getFeedback() == null || q.getFeedback().isBlank()) {
                continue;
            }
            AnswerEvaluation evaluation = readJson(q.getFeedback(), AnswerEvaluation.class);
            if (evaluation != null) {
                sum += evaluation.communication();
                count++;
            }
        }
        return count == 0 ? null : Math.round((float) sum / count);
    }

    private MockInterview loadOwned(Student student, String id) {
        MockInterview interview = interviewRepository.findById(id)
            .orElseThrow(() -> new MockInterviewNotFoundException(id));
        if (!student.getId().equals(interview.getStudentId())) {
            // A run that isn't the caller's own is indistinguishable from a missing one.
            throw new MockInterviewNotFoundException(id);
        }
        return interview;
    }

    private List<MockInterviewQuestion> orderedQuestions(String interviewId) {
        return questionRepository.findByMockInterviewIdOrderByOrdinalAsc(interviewId);
    }

    /** Short, grounded resume-ish context from the student's own stored signals (never fabricated). */
    private String buildResumeContext(Student student) {
        List<String> parts = new ArrayList<>();
        if (student.getTargetRole() != null && !student.getTargetRole().isBlank()) {
            parts.add("Target role: " + student.getTargetRole());
        }
        if (student.getBranch() != null && !student.getBranch().isBlank()) {
            parts.add("Branch: " + student.getBranch());
        }
        List<String> strengths = readList(student.getStrengthsJson());
        if (!strengths.isEmpty()) {
            parts.add("Strengths: " + String.join(", ", strengths));
        }
        return String.join(". ", parts);
    }

    private MockInterviewResponse toResponse(MockInterview interview, List<MockInterviewQuestion> questions) {
        List<MockInterviewQuestionResponse> questionResponses = questions.stream()
            .map(this::toQuestionResponse)
            .toList();
        return new MockInterviewResponse(
            interview.getId(),
            interview.getType(),
            interview.getRole(),
            interview.isUseResume(),
            interview.getStatus(),
            interview.getCurrentQuestion(),
            interview.getOverallScore(),
            readList(interview.getStrengths()),
            readList(interview.getWeaknesses()),
            readList(interview.getRecommendations()),
            interview.getSummary(),
            interview.getCreatedAt(),
            interview.getUpdatedAt(),
            questionResponses);
    }

    private MockInterviewQuestionResponse toQuestionResponse(MockInterviewQuestion q) {
        AnswerFeedback feedback = null;
        if (q.getFeedback() != null && !q.getFeedback().isBlank()) {
            AnswerEvaluation evaluation = readJson(q.getFeedback(), AnswerEvaluation.class);
            if (evaluation != null) {
                feedback = AnswerFeedback.from(evaluation);
            }
        }
        return new MockInterviewQuestionResponse(
            q.getId(),
            q.getOrdinal(),
            q.getPrompt(),
            q.getDifficulty(),
            q.getTimerSeconds(),
            q.isCoding(),
            q.getLanguage(),
            q.getStudentAnswer(),
            q.getScore(),
            feedback);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize mock-interview JSON: {}", e.getMessage());
            return null;
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse mock-interview JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list == null ? List.of() : list;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to parse mock-interview string list: {}", e.getMessage());
            return List.of();
        }
    }
}
