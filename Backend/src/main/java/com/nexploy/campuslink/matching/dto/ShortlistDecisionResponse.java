package com.nexploy.campuslink.matching.dto;

import com.nexploy.campuslink.matching.entity.ShortlistDecision;

/** The recorded decision plus the resulting application status (null if no application existed). */
public record ShortlistDecisionResponse(
    String id,
    String driveId,
    String studentId,
    String decision,
    String reason,
    String decidedBy,
    String applicationStatus
) {
    public static ShortlistDecisionResponse of(ShortlistDecision decision, String applicationStatus) {
        return new ShortlistDecisionResponse(
            decision.getId(),
            decision.getDriveId(),
            decision.getStudentId(),
            decision.getDecision(),
            decision.getReason(),
            decision.getDecidedBy(),
            applicationStatus);
    }
}
