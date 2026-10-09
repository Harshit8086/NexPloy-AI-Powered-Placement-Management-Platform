package com.nexploy.campuslink.readiness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Records one raw piece of skill evidence for a student. Persisted as-is (append-only); readiness
 * scoring reads but never mutates it.
 */
public record RecordSkillEvidenceRequest(
    @NotBlank String skill,
    @NotBlank String source,
    @NotNull @Min(0) @Max(100) Integer level,
    @Size(max = 500) String detail
) {
}
