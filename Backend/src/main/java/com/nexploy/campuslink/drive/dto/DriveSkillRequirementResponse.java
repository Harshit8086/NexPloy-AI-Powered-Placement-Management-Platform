package com.nexploy.campuslink.drive.dto;

import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import java.time.OffsetDateTime;

public record DriveSkillRequirementResponse(
    String id,
    String driveId,
    String skill,
    int requiredLevel,
    int weight,
    boolean mandatory,
    String source,
    Integer confidence,
    boolean approved,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static DriveSkillRequirementResponse from(DriveSkillRequirement entity) {
        return new DriveSkillRequirementResponse(
            entity.getId(),
            entity.getDriveId(),
            entity.getSkill(),
            entity.getRequiredLevel() != null ? entity.getRequiredLevel() : 0,
            entity.getWeight() != null ? entity.getWeight() : 1,
            entity.getMandatory() != null ? entity.getMandatory() : true,
            entity.getSource() != null ? entity.getSource() : "OFFICER",
            entity.getConfidence(),
            entity.getApproved() != null ? entity.getApproved() : true,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
