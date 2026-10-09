package com.nexploy.campuslink.risk.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Frontend-facing view of an intervention plan (camelCase). Maps 1:1 to the TypeScript
 * `InterventionPlan` shape: id, studentId, mentorId, status, createdOn, focusAreas, actions,
 * progress, notes. The service parses the stored JSON columns and passes the lists in.
 */
public record InterventionPlanResponse(
    String id,
    String studentId,
    String mentorId,
    String riskEntryId,
    String status,
    OffsetDateTime createdOn,
    List<String> focusAreas,
    List<InterventionAction> actions,
    int progress,
    String notes
) {
}
