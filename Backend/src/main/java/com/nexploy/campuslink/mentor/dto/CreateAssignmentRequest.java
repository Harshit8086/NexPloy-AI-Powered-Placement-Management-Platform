package com.nexploy.campuslink.mentor.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAssignmentRequest(
    @NotBlank String mentorUsername,
    @NotBlank String studentId
) {
}
