package com.nexploy.campuslink.placement.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for PATCH /api/placement-cycles/{id}/status. */
public record ChangeCycleStatusRequest(@NotBlank String status) {
}
