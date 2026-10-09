package com.nexploy.campuslink.readiness.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * Assessment inputs for a readiness score. Each dimension is optional (0-100); the ML
 * service records missing dimensions rather than treating them as low scores.
 */
public record ReadinessScoreRequestBody(
    @DecimalMin("0") @DecimalMax("100") Double academic,
    @DecimalMin("0") @DecimalMax("100") Double aptitude,
    @DecimalMin("0") @DecimalMax("100") Double communication,
    @DecimalMin("0") @DecimalMax("100") Double projects,
    @DecimalMin("0") @DecimalMax("100") Double interview
) {
}
