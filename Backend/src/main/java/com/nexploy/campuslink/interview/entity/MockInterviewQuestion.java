package com.nexploy.campuslink.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One question within a {@link MockInterview}, plus the student's answer and its evaluation. Rows are
 * created up-front when the interview starts (unanswered), then filled in as the student progresses.
 * The FK to the parent is a plain string column, matching the rest of the module.
 */
@Entity
@Table(name = "mock_interview_questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterviewQuestion {

    @Id
    private String id;

    private String mockInterviewId;

    /** 0-based position within the interview. */
    private int ordinal;

    @Column(columnDefinition = "text")
    private String prompt;

    /** easy | medium | hard (advisory, from the generator). */
    private String difficulty;

    /** Suggested answer time budget in seconds. */
    private Integer timerSeconds;

    /** A coding question is answered in the editor and scored as text (never executed). */
    private boolean isCoding;

    /** Programming language for a coding question, e.g. "java"; null for prose questions. */
    private String language;

    @Column(columnDefinition = "text")
    private String studentAnswer;

    /** 0-100 score for this answer, set once evaluated. */
    private Integer score;

    /** JSON: the nine metric sub-scores + feedback text + improvement bullets. */
    @Column(columnDefinition = "text")
    private String feedback;

    private OffsetDateTime answeredAt;
}
