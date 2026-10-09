package com.nexploy.campuslink.aptitude.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of POST /practice — starts an AI-generated practice attempt. Practice attempts never feed
 * placement readiness.
 */
public record StartPracticeRequest(
    @NotBlank String category,
    @Min(1) @Max(20) int count
) {
}
