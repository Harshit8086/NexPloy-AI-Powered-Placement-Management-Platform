package com.nexploy.campuslink.skillgap.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Inputs for a skill-gap analysis against a target role. The caller supplies the role's required
 * level for each skill (0-100). {@code currentLevel} is optional: when omitted it is sourced from
 * the student's own stored skills ({@code student_skills}), defaulting to 0 for a skill the student
 * has not recorded — so "My Skills" feeds the analysis directly. A caller may still pass an explicit
 * {@code currentLevel} to override the stored value (e.g. a what-if projection). The analysis itself
 * is deterministic and explainable (PRD section 5.2), like eligibility.
 */
public record SkillGapRequest(
    @NotBlank String targetRole,
    @NotEmpty @Valid List<SkillInput> skills
) {

    public record SkillInput(
        @NotBlank String skill,
        @Min(0) @Max(100) Integer currentLevel,
        @NotNull @Min(0) @Max(100) Integer requiredLevel
    ) {
    }
}
