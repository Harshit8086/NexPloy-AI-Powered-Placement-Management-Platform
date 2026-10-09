package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Sets the status of one roadmap task for the signed-in student. Only the two lifecycle states a
 * student toggles are accepted; the task itself is addressed by the path variable.
 *
 * @param status PLANNED or DONE
 */
public record RoadmapTaskStatusRequest(
    @NotBlank @Pattern(regexp = "PLANNED|DONE", message = "must be PLANNED or DONE") String status) {}
