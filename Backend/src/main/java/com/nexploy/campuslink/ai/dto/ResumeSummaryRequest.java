package com.nexploy.campuslink.ai.dto;

import java.util.List;

public record ResumeSummaryRequest(
    String name,
    String targetRole,
    String skills,
    List<ExperienceItem> experience,
    List<ProjectItem> projects,
    List<EducationItem> education
) {
    public record ExperienceItem(
        String role,
        String company,
        String duration,
        String description
    ) {}

    public record ProjectItem(
        String name,
        String techStack,
        String description
    ) {}

    public record EducationItem(
        String degree,
        String branch,
        String college,
        String year,
        String cgpa
    ) {}
}
