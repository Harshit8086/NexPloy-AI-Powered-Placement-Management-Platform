package com.nexploy.campuslink.student.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Student self-service profile edit (PUT /api/students/me). Every field is optional: a null means
 * "leave unchanged", so the client may send a partial patch. Only fields a student legitimately
 * self-reports are here.
 *
 * <p>V60 directory ownership: the academic/identity fields — name, rollNumber, email, branch,
 * branchCode, department, batch, semester, graduationYear, yearLabel, cgpa, 10th/12th, backlogs,
 * backlogHistory — live in the officer-managed campus directory and are deliberately NOT part of
 * this record anymore. A student cannot self-report them (even if an old client still sends them,
 * Jackson ignores unknown JSON properties and nothing applies them); corrections flow through the
 * officer's directory endpoints.
 */
public record StudentUpdateRequest(
    @Size(max = 32) String phone,
    @Size(max = 120) String targetRole,
    List<@Size(max = 120) String> preferredRoles,
    List<@Size(max = 120) String> preferredDomains,
    List<@Size(max = 120) String> preferredLocations,
    List<@Size(max = 120) String> strengths,
    @Valid List<StudentProject> projects,
    @Valid List<StudentExperience> experience,
    @Size(max = 500) String linkedinUrl,
    @Size(max = 500) String githubUrl
) {
}
