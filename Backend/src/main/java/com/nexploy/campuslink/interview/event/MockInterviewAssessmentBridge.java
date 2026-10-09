package com.nexploy.campuslink.interview.event;

import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.service.PreparedAssessmentRow;
import com.nexploy.campuslink.assessment.service.StudentAssessmentWriter;
import com.nexploy.campuslink.readiness.service.ReadinessService;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Optional bridge that folds a completed AI mock interview into the student's assessment history so
 * it contributes to readiness. OFF by default: this bean only exists when
 * {@code nexploy.readiness.mock-interview.feeds-assessment=true}, because the mock-interview module is
 * otherwise deliberately walled off from readiness (it is practice evidence, not a graded assessment).
 *
 * <p>When enabled it writes, on the run's completion date, a {@code MOCK_INTERVIEW} record from the
 * overall score and a {@code COMMUNICATION} record from the averaged per-answer communication metric,
 * then recomputes readiness. Both use a fixed assessment name so a second run on the same day upserts
 * (business key {@code student, type, name, date}) rather than piling up duplicates.
 *
 * <p>Runs after the completion commits ({@link TransactionPhase#AFTER_COMMIT}) on the shared
 * best-effort recompute pool, and is entirely non-fatal: any failure leaves the mock interview and the
 * prior readiness score untouched and is logged, never propagated. Model output stays advisory — it
 * feeds the descriptive readiness score only and never mutates authorization, eligibility or offers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nexploy.readiness.mock-interview.feeds-assessment", havingValue = "true")
public class MockInterviewAssessmentBridge {

    private static final String SOURCE = "MOCK_INTERVIEW_AI";
    private static final String OVERALL_NAME = "AI Mock Interview";
    private static final String COMMUNICATION_NAME = "AI Mock Interview — Communication";
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    private final StudentRepository studentRepository;
    private final StudentAssessmentWriter assessmentWriter;
    private final ReadinessService readinessService;

    @Async("readinessRecomputeExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(MockInterviewCompletedEvent event) {
        List<PreparedAssessmentRow> rows = new ArrayList<>();
        if (event.overallScore() != null) {
            rows.add(row(AssessmentType.MOCK_INTERVIEW, OVERALL_NAME, event.overallScore(), event));
        }
        if (event.communicationScore() != null) {
            rows.add(row(AssessmentType.COMMUNICATION, COMMUNICATION_NAME, event.communicationScore(), event));
        }
        if (rows.isEmpty()) {
            return;
        }

        try {
            Optional<Student> student = studentRepository.findById(event.studentId());
            if (student.isEmpty()) {
                log.warn("Mock-interview bridge: student {} no longer exists; skipping", event.studentId());
                return;
            }
            assessmentWriter.applyForStudent(student.get(), rows);
        } catch (RuntimeException ex) {
            log.warn("Mock-interview bridge: failed to write assessment rows for student {} (skipped): {}",
                event.studentId(), ex.getMessage());
            return;
        }

        try {
            readinessService.recomputeInternal(event.studentId(), "current");
        } catch (RuntimeException ex) {
            log.warn("Mock-interview bridge: readiness recompute failed for student {} (kept prior score): {}",
                event.studentId(), ex.getMessage());
        }
    }

    private PreparedAssessmentRow row(AssessmentType type, String name, int score,
                                      MockInterviewCompletedEvent event) {
        int clamped = Math.max(0, Math.min(100, score));
        return new PreparedAssessmentRow(
            type,
            name,
            BigDecimal.valueOf(clamped),
            MAX_SCORE,
            clamped,
            event.completedAt().toLocalDate(),
            SOURCE);
    }
}
