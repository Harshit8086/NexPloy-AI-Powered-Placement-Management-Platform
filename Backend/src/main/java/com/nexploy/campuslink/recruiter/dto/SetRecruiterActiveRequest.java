package com.nexploy.campuslink.recruiter.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for PATCH /api/recruiters/{id}/active — (de)activate a recruiter (soft-delete). */
public record SetRecruiterActiveRequest(@NotNull Boolean active) {
}
