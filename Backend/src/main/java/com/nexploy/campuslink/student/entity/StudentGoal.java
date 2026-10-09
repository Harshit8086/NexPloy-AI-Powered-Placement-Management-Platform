package com.nexploy.campuslink.student.entity;

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
 * The growth roadmap a student commits to via "Apply This Roadmap" in the What-If Simulator
 * (Phase 3b). It captures the student's own chosen goal: the What-If factors they selected, the
 * projected placement probability that scenario yields, and the role it targets. One active row
 * per student, upserted in place on each re-apply, so the applied roadmap survives refresh/login.
 *
 * <p>This is a self-reported intent record only. Persisting a goal here never feeds — and must not
 * be confused with — the authoritative readiness/assessment inputs that drive digital-twin and
 * skill-gap calculations.
 */
@Entity
@Table(name = "student_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentGoal {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** JSON array of selected What-If factor keys, e.g. {@code ["aws","dsa"]}. */
    @Column(columnDefinition = "text")
    private String factorsJson;

    /** Role the roadmap targets, captured from the student's target role at apply time. */
    private String targetRole;

    private Integer baselineProbability;
    private Integer projectedProbability;
    private Integer readinessImpact;

    @Column(columnDefinition = "text")
    private String recommendedPath;

    /** Lifecycle state; currently always ACTIVE when a roadmap is applied. */
    private String status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
