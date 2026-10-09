package com.nexploy.campuslink.campus.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for PATCH /api/campuses/{id}/active — (de)activate a campus (soft-delete). */
public record SetCampusActiveRequest(@NotNull Boolean active) {
}
