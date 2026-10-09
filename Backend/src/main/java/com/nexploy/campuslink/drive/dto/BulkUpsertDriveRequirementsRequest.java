package com.nexploy.campuslink.drive.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkUpsertDriveRequirementsRequest(
    @NotEmpty(message = "Requirements list cannot be empty")
    List<@Valid UpsertDriveSkillRequirementRequest> requirements
) {}
