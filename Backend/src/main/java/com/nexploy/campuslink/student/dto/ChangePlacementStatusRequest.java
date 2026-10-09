package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PATCH /api/students/{id}/placement-status (officer/admin). The status is one of
 * UNPLACED / IN PROCESS / OFFERED / PLACED; validated server-side.
 */
public record ChangePlacementStatusRequest(@NotBlank String placementStatus) {
}
