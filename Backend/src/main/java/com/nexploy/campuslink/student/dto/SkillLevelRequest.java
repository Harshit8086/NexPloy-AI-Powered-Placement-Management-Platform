package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body for nudging a VERIFIED skill's level without re-testing (PUT /me/skills/{skillId}/level).
 * Only small corrections are accepted; anything larger must come from a fresh skill viva so the
 * verified badge keeps its meaning.
 */
public record SkillLevelRequest(
    @NotNull @Min(0) @Max(100) Integer level
) {
}