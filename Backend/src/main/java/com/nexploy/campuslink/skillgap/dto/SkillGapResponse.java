package com.nexploy.campuslink.skillgap.dto;

import java.util.List;

/**
 * Skill-gap analysis for a student against a target role or drive requirements.
 * Maps 1:1 to the frontend {@code SkillGap[]} shape. Gaps are ordered by priority
 * (HIGH first), then by gap size or importance weight.
 */
public record SkillGapResponse(
    String studentId,
    String targetRole,
    List<SkillGapItem> skillGaps
) {

    /**
     * One skill evaluated against requirements.
     * When a student lacks evidence for a skill, {@code currentLevel} is NOT assumed to be 0;
     * instead, {@code evidenceStatus} is {@code "NO_EVIDENCE"} / {@code "UNKNOWN"} and
     * {@code currentLabel} indicates that evidence has not been recorded yet.
     */
    public record SkillGapItem(
        String skill,
        Integer currentLevel,
        Integer targetLevel,
        String currentLabel,
        String targetLabel,
        String priority,
        String whyItMatters,
        String recommendedAction,
        String verificationStatus,
        String evidenceStatus
    ) {
        /** Backward-compatible 9-argument constructor. */
        public SkillGapItem(
            String skill,
            Integer currentLevel,
            Integer targetLevel,
            String currentLabel,
            String targetLabel,
            String priority,
            String whyItMatters,
            String recommendedAction,
            String verificationStatus
        ) {
            this(
                skill,
                currentLevel,
                targetLevel,
                currentLabel,
                targetLabel,
                priority,
                whyItMatters,
                recommendedAction,
                verificationStatus,
                (currentLevel == null || "None".equalsIgnoreCase(currentLabel) || "Missing".equalsIgnoreCase(currentLabel))
                    ? "NO_EVIDENCE"
                    : (verificationStatus != null ? verificationStatus : "EVIDENCED")
            );
        }
    }
}
