package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A student's work experience / internship entry. Maps 1:1 to the frontend StudentExperience.
 * Doubles as an input element in {@code StudentUpdateRequest}; the bounds below only fire on a
 * validated request body, never on response serialization.
 */
public record StudentExperience(
    @NotBlank @Size(max = 200) String company,
    @Size(max = 200) String role,
    @Size(max = 120) String duration,
    @Size(max = 2000) String summary
) {
}
