package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.NotNull;

/** Soft-active toggle for a directory record (V60): deactivate instead of hard-delete. */
public record SetStudentActiveRequest(@NotNull Boolean active) {
}
