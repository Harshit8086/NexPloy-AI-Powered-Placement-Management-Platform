package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Adds or updates one of the signed-in student's skills. Upsert is keyed on {@code skill}
 * (case-insensitive), so re-submitting an existing skill updates its proficiency in place.
 *
 * @param skill       the skill label (required)
 * @param proficiency self-rated level 0-100 (required)
 * @param source      how the level was set; optional, defaults to {@code SELF_REPORTED}
 */
public record SkillUpsertRequest(
    @NotBlank @Size(max = 120) String skill,
    @NotNull @Min(0) @Max(100) Integer proficiency,
    @Size(max = 32) String source
) {
}
