package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Applies (or re-applies) the signed-in student's growth roadmap from the What-If Simulator
 * (Phase 3b). The scenario is computed client-side, so the request carries both the selected
 * factors and the projected numbers that scenario produced; the backend persists them as the
 * student's chosen goal. There is one active roadmap per student — re-applying replaces it.
 *
 * @param factors              selected What-If factor keys (e.g. "aws", "dsa"); at least one
 * @param projectedProbability projected placement probability for the scenario, 0-100
 * @param baselineProbability  the student's current baseline probability, 0-100 (optional)
 * @param readinessImpact      projected readiness change for the scenario (optional)
 * @param recommendedPath      the recommended-path note shown alongside the scenario (optional)
 */
public record ApplyRoadmapRequest(
    @NotEmpty @Size(max = 20) List<@NotEmpty @Size(max = 64) String> factors,
    @Min(0) @Max(100) Integer projectedProbability,
    @Min(0) @Max(100) Integer baselineProbability,
    Integer readinessImpact,
    @Size(max = 2000) String recommendedPath
) {
}
