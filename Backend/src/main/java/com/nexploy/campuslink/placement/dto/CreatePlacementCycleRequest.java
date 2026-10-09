package com.nexploy.campuslink.placement.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * Request to create a placement cycle. campusId is derived from the actor (officers act on
 * their own campus; admins may pass an explicit campusId). status starts at PLANNING.
 */
public record CreatePlacementCycleRequest(
    @NotBlank String name,
    @NotBlank String academicYear,
    String campusId,
    LocalDate startsOn,
    LocalDate endsOn
) {
}
