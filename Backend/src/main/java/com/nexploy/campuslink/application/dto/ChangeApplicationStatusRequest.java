package com.nexploy.campuslink.application.dto;

import com.nexploy.campuslink.application.entity.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

/** Request body for PATCH /api/applications/{id}/status — the target funnel stage. */
public record ChangeApplicationStatusRequest(@NotNull ApplicationStatus status) {
}
