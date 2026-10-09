package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Adds a portfolio project to the signed-in student's roadmap — used by the resume analysis
 * "Add to Roadmap" action and the roadmap's own recommended-project cards. The project is appended as
 * a PROJECT-kind task; duplicates (same title, case-insensitive) are ignored server-side so the
 * button is idempotent.
 *
 * @param name               project title (required)
 * @param description         what to build; optional
 * @param addressesGap        the gap/skill this project closes; optional
 * @param skillsDemonstrated  skills the project would demonstrate; optional
 * @param difficulty          Beginner/Intermediate/Advanced; optional
 */
public record RoadmapProjectRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 600) String description,
    @Size(max = 200) String addressesGap,
    @Size(max = 20) List<@Size(max = 120) String> skillsDemonstrated,
    @Size(max = 40) String difficulty) {}
