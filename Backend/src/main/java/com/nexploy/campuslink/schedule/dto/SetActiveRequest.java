package com.nexploy.campuslink.schedule.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for PATCH .../active on a schedule resource (venue/panel) — soft-delete toggle. */
public record SetActiveRequest(@NotNull Boolean active) {
}
