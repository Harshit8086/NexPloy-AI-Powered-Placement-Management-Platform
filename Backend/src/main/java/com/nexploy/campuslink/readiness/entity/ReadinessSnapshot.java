package com.nexploy.campuslink.readiness.entity;

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
 * A derived readiness result, persisted as append-only history (DESIGN.md section 2). Each
 * scoring call writes a new snapshot rather than overwriting the previous one, so the record of
 * how a student's readiness was computed over time is preserved. A snapshot stores the score and
 * category, the eight component scores (as JSON), the ids of the raw evidence it was computed
 * from, the calculation/model/feature versions, and the generated timestamp — mirroring the
 * fields DESIGN.md section 2 requires. It never mutates {@link SkillEvidence}.
 */
@Entity
@Table(name = "readiness_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadinessSnapshot {

    @Id
    private String id;

    private String studentId;
    private String campusId;
    private String placementCycleId;

    /** Optional FK to placement_cycles (nullable, dual with the legacy placementCycleId string). */
    private String cycleId;

    private double score;

    /** NOT_READY, DEVELOPING, READY, HIGHLY_EMPLOYABLE. */
    private String category;

    /** The eight component scores, serialized as a JSON object. */
    @Column(columnDefinition = "text")
    private String componentScoresJson;

    /** Comma-separated ids of the {@link SkillEvidence} rows this snapshot was derived from. */
    @Column(length = 2000)
    private String evidenceIds;

    private double confidence;

    private String calculationVersion;
    private String modelVersion;
    private String featureVersion;

    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
}
