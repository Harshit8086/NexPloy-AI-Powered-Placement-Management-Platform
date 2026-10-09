package com.nexploy.campuslink.aptitude.event;

import com.nexploy.campuslink.aptitude.service.AptitudeTestCatalog;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.service.PreparedAssessmentRow;
import com.nexploy.campuslink.assessment.service.StudentAssessmentWriter;
import com.nexploy.campuslink.readiness.service.ReadinessService;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
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
 * Optional bridge that folds a completed official aptitude test into the student's assessment
 * history so it contributes to readiness. OFF by default: this bean only exists when
 * {@code nexploy.readiness.aptitude.feeds-assessment=true}, mirroring the mock-interview bridge.
 *
 * <p>When enabled it writes, on the attempt's completion date, an {@code APTITUDE} record carrying
 * the deterministic bank-based score, then recomputes readiness. The assessment name is the catalog
 * test's title (not a fixed string), so two different tests taken on the same day upsert into
 * distinct rows (business key {@code student, type, name, date}) instead of clobbering each other.
 *
 * <p>Runs after the submission commits ({@link TransactionPhase#AFTER_COMMIT}) on the shared
 * best-effort recompute pool, and is entirely non-fatal: any failure leaves the attempt and the
 * prior readiness score untouched and is logged, never propagated. The score is a deterministic
 * bank result — but like every assessment feed it stays descriptive evidence and never mutates
 * authorization, eligibility or offers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nexploy.readiness.aptitude.feeds-assessment", havingValue = "true")
public class AptitudeAssessmentBridge {

    private static final String SOURCE = "APTITUDE_TEST";
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    private final AptitudeTestCatalog catalog;
    private final StudentRepository studentRepository;
    private final StudentAssessmentWriter assessmentWriter;
    private final ReadinessService readinessService;

    @Async("readinessRecomputeExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(AptitudeAttemptCompletedEvent event) {
        if (event.score() == null) {
            return;
        }
        String name = catalog.byId(event.testId())
            .map(AptitudeTestCatalog.TestDefinition::title)
            .orElse("Aptitude Test");
        int percent = event.score().max(BigDecimal.ZERO).min(MAX_SCORE).intValue();

        try {
            Optional<Student> student = studentRepository.findById(event.studentId());
            if (student.isEmpty()) {
                log.warn("Aptitude bridge: student {} no longer exists; skipping", event.studentId());
                return;
            }
            PreparedAssessmentRow row = new PreparedAssessmentRow(
                AssessmentType.APTITUDE,
                name,
                event.score(),
                MAX_SCORE,
                percent,
                event.completedAt().toLocalDate(),
                SOURCE);
            assessmentWriter.applyForStudent(student.get(), List.of(row));
        } catch (RuntimeException ex) {
            log.warn("Aptitude bridge: failed to write assessment row for student {} (skipped): {}",
                event.studentId(), ex.getMessage());
            return;
        }

        try {
            readinessService.recomputeInternal(event.studentId(), "current");
        } catch (RuntimeException ex) {
            log.warn("Aptitude bridge: readiness recompute failed for student {} (kept prior score): {}",
                event.studentId(), ex.getMessage());
        }
    }
}
