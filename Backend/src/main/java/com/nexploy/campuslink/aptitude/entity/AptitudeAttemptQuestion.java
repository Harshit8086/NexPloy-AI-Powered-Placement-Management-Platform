package com.nexploy.campuslink.aptitude.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Snapshot of one question drawn into an attempt, plus the student's interaction with it. The
 * question content itself stays in aptitude_questions; this row only records the draw order,
 * the selected option, the mark-for-review flag and — filled at submit — whether it was correct.
 */
@Entity
@Table(name = "aptitude_attempt_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptitudeAttemptQuestion {

    @Id
    private String id;

    private String attemptId;
    private String questionId;

    /** 1-based position in the shuffled draw. */
    private int ordinal;

    /** 'A'..'D', or NULL while unanswered. */
    @Column(length = 1)
    private String selectedOption;

    /** Student marked this question for review. */
    private boolean marked;

    /** Filled at submit by deterministic comparison with the question's correctOption. */
    private Boolean correct;
}
