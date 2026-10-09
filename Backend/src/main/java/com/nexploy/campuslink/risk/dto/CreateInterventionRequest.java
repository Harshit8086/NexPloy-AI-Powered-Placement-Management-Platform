package com.nexploy.campuslink.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Creates an intervention plan for a student in response to placement risk. The plan starts in
 * PROPOSED. Focus areas and action labels are supplied by the officer/mentor raising it; each
 * action label becomes an unchecked action item. Optionally links the risk entry it was raised
 * from for traceability.
 */
public record CreateInterventionRequest(
    @NotBlank String mentorUsername,
    String riskEntryId,
    @NotEmpty List<@NotBlank String> focusAreas,
    @NotEmpty List<@NotBlank String> actionLabels,
    @Size(max = 2000) String notes
) {
}
