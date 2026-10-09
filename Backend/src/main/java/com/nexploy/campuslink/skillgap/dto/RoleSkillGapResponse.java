package com.nexploy.campuslink.skillgap.dto;

import com.nexploy.campuslink.skillgap.dto.SkillGapResponse.SkillGapItem;
import java.util.List;

/**
 * Server-resolved skill-gap analysis for a student against a specific drive or target role.
 * Derived from approved per-drive skill requirements ({@code drive_skill_requirements})
 * or presence-only matching against {@code Drive.requiredSkillsJson}.
 */
public record RoleSkillGapResponse(
    String studentId,
    String targetRole,
    boolean hasTargetRole,
    boolean hasSkills,
    boolean roleRequirementsAvailable,
    boolean driveSkillsFallback,
    int overallMatch,
    List<SkillMatchItem> strengths,
    List<SkillGapItem> gaps,
    String driveId,
    String companyName,
    Integer coveredSkillsCount,
    Integer totalSkillsCount,
    String analysisType
) {

    /** Backward-compatible constructor for existing tests and client consumers. */
    public RoleSkillGapResponse(
        String studentId,
        String targetRole,
        boolean hasTargetRole,
        boolean hasSkills,
        boolean roleRequirementsAvailable,
        boolean driveSkillsFallback,
        int overallMatch,
        List<SkillMatchItem> strengths,
        List<SkillGapItem> gaps
    ) {
        this(
            studentId,
            targetRole,
            hasTargetRole,
            hasSkills,
            roleRequirementsAvailable,
            driveSkillsFallback,
            overallMatch,
            strengths,
            gaps,
            null,
            null,
            null,
            null,
            driveSkillsFallback ? "PRESENCE_ONLY" : "LEVEL_BASED"
        );
    }

    /**
     * A skill the student already meets or exceeds for the requirement — surfaced as a strength.
     */
    public record SkillMatchItem(
        String skill,
        int currentLevel,
        int requiredLevel,
        String verificationStatus,
        String evidenceStatus
    ) {
        public SkillMatchItem(String skill, int currentLevel, int requiredLevel, String verificationStatus) {
            this(skill, currentLevel, requiredLevel, verificationStatus,
                verificationStatus != null ? verificationStatus : "VERIFIED");
        }
    }
}
