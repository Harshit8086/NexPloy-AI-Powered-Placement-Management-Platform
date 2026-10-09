package com.nexploy.campuslink.aptitude.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One sitting of an aptitude test by a student. OFFICIAL attempts are bank-drawn and, once scored
 * at submit, may feed the APTITUDE readiness dimension; PRACTICE attempts are AI-generated
 * self-practice and never publish the completion event. Scoring is deterministic (percent
 * correct) — no AI is involved in grading.
 */
@Entity
@Table(name = "aptitude_attempts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptitudeAttempt {

    @Id
    private String id;

    private String studentId;

    /** Catalog test id; the literal 'PRACTICE' on the practice lane. */
    private String testId;

    @Enumerated(EnumType.STRING)
    private AptitudeAttemptMode mode;

    /** Single category, or NULL for the mixed grand test. */
    private String category;

    @Enumerated(EnumType.STRING)
    private AptitudeAttemptStatus status;

    private int totalQuestions;

    /** Set at submit. */
    private Integer correctCount;

    /** 0..100, deterministic percent correct, set at submit. */
    private BigDecimal score;

    private int durationLimitSeconds;

    /** Client-reported, clamped to durationLimitSeconds + grace at submit. */
    private Integer timeSpentSeconds;

    /** students.assessment_aptitude captured at submit, before the readiness feed. */
    private BigDecimal aptitudeBefore;

    /** Completed attempt whose missed questions a retry attempt re-draws; NULL otherwise. */
    private String sourceAttemptId;

    private OffsetDateTime startedAt;
    private OffsetDateTime submittedAt;
}
