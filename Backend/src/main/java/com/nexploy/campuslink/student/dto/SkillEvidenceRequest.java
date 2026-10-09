package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for attaching a proof link to a skill (PUT /me/skills/{skillId}/evidence). The URL must be an
 * https link to a project, repository or certificate; the backend validates the scheme. A valid
 * attach promotes the skill to EVIDENCED (unless already VERIFIED).
 */
public record SkillEvidenceRequest(
    @NotBlank @Size(max = 500) String url,
    @Size(max = 300) String note
) {
}
