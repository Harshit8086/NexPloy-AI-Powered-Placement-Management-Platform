package com.nexploy.campuslink.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.ai.service.PromptInjectionGuard;
import com.nexploy.campuslink.interview.ai.RuleBasedMockInterviewAi;
import com.nexploy.campuslink.interview.dto.MockInterviewResponse;
import com.nexploy.campuslink.interview.dto.StartMockInterviewRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerResponse;
import com.nexploy.campuslink.interview.entity.MockInterview;
import com.nexploy.campuslink.interview.entity.MockInterviewQuestion;
import com.nexploy.campuslink.interview.entity.MockInterviewStatus;
import com.nexploy.campuslink.interview.entity.MockInterviewType;
import com.nexploy.campuslink.interview.exception.InvalidMockInterviewStateException;
import com.nexploy.campuslink.interview.exception.MockInterviewNotFoundException;
import com.nexploy.campuslink.interview.repository.MockInterviewQuestionRepository;
import com.nexploy.campuslink.interview.repository.MockInterviewRepository;
import com.nexploy.campuslink.student.entity.Student;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for {@link MockInterviewService} on the offline {@link RuleBasedMockInterviewAi} path
 * (the default when no AI key is set): starting generates and persists the templated question set,
 * answering scores and advances, the last answer finalises the report, and ownership is enforced so
 * another student's run is indistinguishable from a missing one. Repositories are mocked with a
 * tiny in-memory backing so the service's own read-after-write flow is exercised.
 */
@ExtendWith(MockitoExtension.class)
class MockInterviewServiceTest {

    @Mock
    private MockInterviewRepository interviewRepository;

    @Mock
    private MockInterviewQuestionRepository questionRepository;

    private MockInterviewService service;

    // In-memory backing so findById / findByMockInterviewId reflect what the service saved.
    private MockInterview savedInterview;
    private final List<MockInterviewQuestion> savedQuestions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MockInterviewService(
            interviewRepository,
            questionRepository,
            new RuleBasedMockInterviewAi(),
            new PromptInjectionGuard(),
            new ObjectMapper(),
            // No-op publisher: these unit tests exercise scoring/report logic, not the (separately
            // tested, default-OFF) MockInterviewCompletedEvent -> assessment bridge.
            (ApplicationEventPublisher) event -> {});

        lenient().when(interviewRepository.save(any())).thenAnswer(inv -> {
            savedInterview = inv.getArgument(0);
            return savedInterview;
        });
        lenient().when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(questionRepository.saveAll(any())).thenAnswer(inv -> {
            savedQuestions.clear();
            ((Iterable<MockInterviewQuestion>) inv.getArgument(0)).forEach(savedQuestions::add);
            return savedQuestions;
        });
        lenient().when(interviewRepository.findById(any()))
            .thenAnswer(inv -> savedInterview != null && savedInterview.getId().equals(inv.getArgument(0))
                ? Optional.of(savedInterview) : Optional.empty());
        lenient().when(questionRepository.findByMockInterviewIdOrderByOrdinalAsc(any()))
            .thenAnswer(inv -> new ArrayList<>(savedQuestions));
    }

    private Student student() {
        return Student.builder().id("stu_1").campusId("campus_a").email("s@campus.example")
            .targetRole("Backend Developer").build();
    }

    @Test
    void startGeneratesAndPersistsTheQuestionSet() {
        MockInterviewResponse response = service.start(
            student(), new StartMockInterviewRequest(MockInterviewType.MIXED, "Backend Developer", false));

        assertThat(response.status()).isEqualTo(MockInterviewStatus.IN_PROGRESS);
        assertThat(response.currentQuestion()).isZero();
        // MIXED bank yields six questions, ordinals 0..5, at least one coding prompt.
        assertThat(response.questions()).hasSize(6);
        assertThat(response.questions()).extracting(q -> q.ordinal()).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(response.questions()).anyMatch(q -> q.coding());
        assertThat(savedInterview.getStudentId()).isEqualTo("stu_1");
        assertThat(savedInterview.getCampusId()).isEqualTo("campus_a");
    }

    @Test
    void answeringEveryQuestionFinalisesTheReport() {
        Student student = student();
        MockInterviewResponse started = service.start(
            student, new StartMockInterviewRequest(MockInterviewType.BEHAVIOURAL, "Backend Developer", false));
        int total = started.questions().size();

        SubmitAnswerResponse last = null;
        for (int i = 0; i < total; i++) {
            last = service.submitAnswer(student, savedInterview.getId(),
                new SubmitAnswerRequest("I owned the payments module and shipped it in two sprints with tests."));
        }

        assertThat(last).isNotNull();
        assertThat(last.completed()).isTrue();
        assertThat(last.status()).isEqualTo(MockInterviewStatus.COMPLETED);
        assertThat(savedInterview.getStatus()).isEqualTo(MockInterviewStatus.COMPLETED);
        assertThat(savedInterview.getOverallScore()).isNotNull();
        assertThat(savedInterview.getSummary()).isNotBlank();
    }

    @Test
    void anotherStudentsRunIsNotFound() {
        service.start(student(),
            new StartMockInterviewRequest(MockInterviewType.TECHNICAL, "Backend Developer", false));
        Student intruder = Student.builder().id("stu_2").campusId("campus_a").build();

        assertThatThrownBy(() -> service.get(intruder, savedInterview.getId()))
            .isInstanceOf(MockInterviewNotFoundException.class);
    }

    @Test
    void submittingToACompletedInterviewIsRejected() {
        Student student = student();
        service.start(student,
            new StartMockInterviewRequest(MockInterviewType.BEHAVIOURAL, "Backend Developer", false));
        String id = savedInterview.getId();
        int total = savedQuestions.size();
        for (int i = 0; i < total; i++) {
            service.submitAnswer(student, id, new SubmitAnswerRequest("A reasonably detailed answer here."));
        }

        assertThatThrownBy(() -> service.submitAnswer(student, id,
            new SubmitAnswerRequest("One answer too many.")))
            .isInstanceOf(InvalidMockInterviewStateException.class);
    }
}
