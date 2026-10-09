package com.nexploy.campuslink.readiness.controller;

import com.nexploy.campuslink.readiness.client.MlReadinessClient.ReadinessScore;
import com.nexploy.campuslink.readiness.dto.ReadinessScoreRequestBody;
import com.nexploy.campuslink.readiness.dto.ReadinessSnapshotResponse;
import com.nexploy.campuslink.readiness.dto.RecordSkillEvidenceRequest;
import com.nexploy.campuslink.readiness.dto.SkillEvidenceResponse;
import com.nexploy.campuslink.readiness.service.ReadinessService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/readiness")
@RequiredArgsConstructor
public class ReadinessController {

    private final ReadinessService readinessService;

    @PostMapping
    public ReadinessScore score(
        @PathVariable String studentId,
        @RequestParam(defaultValue = "current") String placementCycleId,
        @Valid @RequestBody ReadinessScoreRequestBody body
    ) {
        return readinessService.score(studentId, placementCycleId, body);
    }

    /**
     * Recomputes the student's readiness from their EXISTING data (assessments/academics/projects),
     * writes it back to the displayed columns, and appends a snapshot. No request body — the inputs
     * are derived server-side, not caller-supplied. Campus-scoped like the sibling scoring endpoint.
     */
    @PostMapping("/recompute")
    public ReadinessScore recompute(
        @PathVariable String studentId,
        @RequestParam(defaultValue = "current") String placementCycleId
    ) {
        return readinessService.recompute(studentId, placementCycleId);
    }

    /** Persisted derived-score history for the student (newest first), per DESIGN.md section 2. */
    @GetMapping("/snapshots")
    public List<ReadinessSnapshotResponse> snapshots(@PathVariable String studentId) {
        return readinessService.listSnapshots(studentId).stream()
            .map(ReadinessSnapshotResponse::from).toList();
    }

    /** Records one raw skill-evidence row (append-only; scoring never overwrites it). */
    @PostMapping("/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillEvidenceResponse recordEvidence(
        @PathVariable String studentId,
        @Valid @RequestBody RecordSkillEvidenceRequest request
    ) {
        return SkillEvidenceResponse.from(readinessService.recordEvidence(studentId, request));
    }

    /** Raw skill evidence on record for the student (newest first). */
    @GetMapping("/evidence")
    public List<SkillEvidenceResponse> listEvidence(@PathVariable String studentId) {
        return readinessService.listEvidence(studentId).stream()
            .map(SkillEvidenceResponse::from).toList();
    }
}
