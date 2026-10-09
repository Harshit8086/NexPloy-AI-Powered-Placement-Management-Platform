package com.nexploy.campuslink.placement.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Request to replace a placement cycle's editable fields (PUT). Status moves via PATCH /status. */
public record UpdatePlacementCycleRequest(
    @NotBlank String name,
    @NotBlank String academicYear,
    LocalDate startsOn,
    LocalDate endsOn
) {
}
