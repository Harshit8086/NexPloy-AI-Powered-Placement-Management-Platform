package com.nexploy.campuslink.matching.entity;

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
 * Durable record of one match scoring for a (drive, student) pair under a given model
 * version. Carries the structured breakdown and the human-readable explanation so a
 * shortlisting decision can be justified and matching accuracy evaluated over time.
 *
 * <p>One row per (drive_id, student_id, model_version) — re-scoring the same model
 * version upserts; a new model version yields a new row so history is preserved.
 */
@Entity
@Table(name = "match_results")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {

    @Id
    private String id;

    private String campusId;
    private String cycleId;
    private String studentId;
    private String driveId;

    private int overallFit;
    private boolean eligibilityPassed;

    /** Structured sub-score breakdown, serialized as JSON. */
    @Column(columnDefinition = "text")
    private String breakdownJson;

    /** Ranked reasons the student is a strong fit, serialized as JSON. */
    @Column(columnDefinition = "text")
    private String whySelectedJson;

    /** Weak or missing signals, serialized as JSON. */
    @Column(columnDefinition = "text")
    private String weakOrMissingJson;

    private String recommendation;
    private String modelVersion;
    private OffsetDateTime computedAt;
}
