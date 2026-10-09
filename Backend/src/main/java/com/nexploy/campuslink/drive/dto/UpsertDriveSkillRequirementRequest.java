package com.nexploy.campuslink.drive.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertDriveSkillRequirementRequest(
    @NotBlank(message = "Skill name cannot be blank")
    @Size(max = 120, message = "Skill name cannot exceed 120 characters")
    String skill,

    @NotNull(message = "Required level is required")
    @Min(value = 0, message = "Required level must be between 0 and 100")
    @Max(value = 100, message = "Required level must be between 0 and 100")
    Integer requiredLevel,

    @Min(value = 1, message = "Weight must be between 1 and 10")
    @Max(value = 10, message = "Weight must be between 1 and 10")
    Integer weight,

    Boolean mandatory,

    String source,

    Integer confidence,

    Boolean approved
) {}
