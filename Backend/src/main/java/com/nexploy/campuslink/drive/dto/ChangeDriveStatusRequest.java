package com.nexploy.campuslink.drive.dto;

import com.nexploy.campuslink.drive.entity.DriveStatus;
import jakarta.validation.constraints.NotNull;

/** Request body for PATCH /api/drives/{id}/status — the target lifecycle state. */
public record ChangeDriveStatusRequest(@NotNull DriveStatus status) {
}
