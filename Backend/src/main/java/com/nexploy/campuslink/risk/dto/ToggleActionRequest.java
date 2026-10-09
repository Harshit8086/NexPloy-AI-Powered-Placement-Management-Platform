package com.nexploy.campuslink.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Marks one action item of an intervention plan done/undone; progress is recomputed. */
public record ToggleActionRequest(
    @NotBlank String actionId,
    @NotNull Boolean done
) {
}
