package com.nexploy.campuslink.readiness.dto;

import com.nexploy.campuslink.readiness.entity.ReadinessSnapshot;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Frontend-facing view of a persisted readiness snapshot (camelCase). Exposes the derived score
 * and its provenance (evidence ids, versions, timestamp) without the raw component JSON blob.
 */
public record ReadinessSnapshotResponse(
    String id,
    String studentId,
    String placementCycleId,
    double score,
    String category,
    double confidence,
    List<String> evidenceIds,
    String calculationVersion,
    String modelVersion,
    String featureVersion,
    OffsetDateTime generatedAt
) {

    public static ReadinessSnapshotResponse from(ReadinessSnapshot snapshot) {
        List<String> ids = snapshot.getEvidenceIds() == null || snapshot.getEvidenceIds().isBlank()
            ? List.of()
            : List.of(snapshot.getEvidenceIds().split(","));
        return new ReadinessSnapshotResponse(
            snapshot.getId(), snapshot.getStudentId(), snapshot.getPlacementCycleId(),
            snapshot.getScore(), snapshot.getCategory(), snapshot.getConfidence(), ids,
            snapshot.getCalculationVersion(), snapshot.getModelVersion(), snapshot.getFeatureVersion(),
            snapshot.getGeneratedAt());
    }
}
