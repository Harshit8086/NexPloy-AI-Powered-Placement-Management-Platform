package com.nexploy.campuslink.readiness.dto;

import com.nexploy.campuslink.readiness.entity.SkillEvidence;
import java.time.OffsetDateTime;

/** Frontend-facing view of a raw skill-evidence record (camelCase). */
public record SkillEvidenceResponse(
    String id,
    String studentId,
    String skill,
    String source,
    Integer level,
    String detail,
    String recordedBy,
    OffsetDateTime recordedAt
) {

    public static SkillEvidenceResponse from(SkillEvidence evidence) {
        return new SkillEvidenceResponse(
            evidence.getId(), evidence.getStudentId(), evidence.getSkill(), evidence.getSource(),
            evidence.getLevel(), evidence.getDetail(), evidence.getRecordedBy(), evidence.getRecordedAt());
    }
}
