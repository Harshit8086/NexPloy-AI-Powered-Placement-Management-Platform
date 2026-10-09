package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A student project. Maps 1:1 to the frontend StudentProject. Doubles as an input element in
 * {@code StudentUpdateRequest}; the bounds below only fire on a validated request body, never on
 * response serialization.
 */
public record StudentProject(
    @NotBlank @Size(max = 200) String name,
    List<@Size(max = 120) String> stack,
    @Size(max = 2000) String summary,
    @Size(max = 500) String githubUrl,
    @Size(max = 500) String liveUrl
) {
}
