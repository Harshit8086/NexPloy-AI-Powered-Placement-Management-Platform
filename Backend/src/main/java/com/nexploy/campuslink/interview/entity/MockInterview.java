package com.nexploy.campuslink.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One AI practice ("mock") interview run for a student — a NEW capability that lives beside, but is
 * strictly separate from, the placement {@link Interview}. A student starts a run for a target role,
 * the AI generates a set of questions ({@link MockInterviewQuestion}), the student answers them one
 * at a time, each answer is scored, and on completion an overall report (score + strengths /
 * weaknesses / recommendations + summary) is written back here.
 *
 * <p>This is descriptive practice history only: it never feeds placement eligibility, offers,
 * readiness or the digital twin. FK columns are plain strings, matching {@link Interview}.
 */
@Entity
@Table(name = "mock_interviews")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockInterview {

    @Id
    private String id;

    private String campusId;
    private String studentId;

    @Enumerated(EnumType.STRING)
    private MockInterviewType type;

    /** Target role the student is practising for, e.g. "Backend Engineer". */
    private String role;

    /** Whether the student opted to ground questions on their stored resume signals. */
    private boolean useResume;

    @Enumerated(EnumType.STRING)
    private MockInterviewStatus status;

    /** Index of the next unanswered question; equals the question count once completed. */
    private int currentQuestion;

    /** 0-100 headline score, set only on completion. */
    private Integer overallScore;

    /** JSON array of strings, set on completion. */
    @Column(columnDefinition = "text")
    private String strengths;

    /** JSON array of strings, set on completion. */
    @Column(columnDefinition = "text")
    private String weaknesses;

    /** JSON array of strings, set on completion. */
    @Column(columnDefinition = "text")
    private String recommendations;

    /** Narrative report, set on completion. */
    @Column(columnDefinition = "text")
    private String summary;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
