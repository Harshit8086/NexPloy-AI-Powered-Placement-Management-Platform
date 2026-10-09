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
 * A student's personalized Career Roadmap (Phase C). One row per student, materialized on first view
 * from the curated {@code RoadmapCatalog} and seeded against the skills the student has already
 * demonstrated, then edited in place as they complete tasks and add projects.
 *
 * <p>Only the durable roadmap <em>structure</em> is persisted — the ordered phases and their tasks
 * with per-task status — serialized into {@code roadmapJson}. Progress percentages, phase status and
 * the recommended-project list are derived at read time from this structure and are never stored as
 * fabricated numbers. The raw resume text is never stored. This is the student's own working plan; it
 * is independent of {@link StudentGoal} (What-If) and never feeds readiness/skill-gap/digital-twin.
 */
@Entity
@Table(name = "career_roadmap")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareerRoadmap {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** Target role captured at materialization time; determines which catalog family was used. */
    private String targetRole;

    /** The full {@code StoredRoadmap} (phases[] -> tasks[] with status) as JSON. Never raw resume text. */
    @Column(columnDefinition = "text")
    private String roadmapJson;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
