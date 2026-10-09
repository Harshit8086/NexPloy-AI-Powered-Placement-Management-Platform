package com.nexploy.campuslink.student.dto;

import com.nexploy.campuslink.student.entity.StudentSkill;
import java.time.OffsetDateTime;

public record StudentSkillResponse(
    String id,
    String skill,
    Integer proficiency,
    String source,
    String verificationStatus,
    String evidenceUrl,
    String evidenceNote,
    OffsetDateTime updatedAt
) {

    public static StudentSkillResponse from(StudentSkill skill) {
        return new StudentSkillResponse(
            skill.getId(),
            skill.getSkill(),
            skill.getProficiency(),
            skill.getSource(),
            skill.getVerificationStatus() == null ? "SELF_DECLARED" : skill.getVerificationStatus(),
            skill.getEvidenceUrl(),
            skill.getEvidenceNote(),
            skill.getUpdatedAt()
        );
    }
}
