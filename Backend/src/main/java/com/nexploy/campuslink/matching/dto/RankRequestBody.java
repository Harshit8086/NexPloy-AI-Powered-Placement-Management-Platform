package com.nexploy.campuslink.matching.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Fit features for the candidates to rank against a drive. Eligibility and readiness are not
 * supplied here: eligibility is computed by Spring's deterministic gate and readiness is read
 * from the student record, so a caller cannot spoof either. Each fit feature is optional
 * (0-100); the ML service records missing features rather than imputing a low fit.
 */
public record RankRequestBody(
    @NotEmpty @Valid List<CandidateInput> candidates
) {

    public record CandidateInput(
        @NotNull String studentId,
        @DecimalMin("0") @DecimalMax("100") Double requiredSkillCoverage,
        @DecimalMin("0") @DecimalMax("100") Double semanticSimilarity,
        @DecimalMin("0") @DecimalMax("100") Double projectRelevance,
        @DecimalMin("0") @DecimalMax("100") Double interviewScore,
        @DecimalMin("0") @DecimalMax("100") Double academics,
        @DecimalMin("0") @DecimalMax("100") Double certificationRelevance
    ) {
    }
}
