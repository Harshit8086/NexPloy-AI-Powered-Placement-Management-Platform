package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Officer/admin manual backlog-timeline entry. Appends a descriptive point to a student's history
 * (e.g. "2 backlogs cleared in Sem 5"); it does NOT change {@code students.backlogs} — the current
 * count is managed through the profile edit endpoints.
 */
public record RecordBacklogHistoryRequest(
    Integer semester,
    @NotNull @Min(0) Integer activeBacklogs,
    @Min(0) Integer clearedBacklogs,
    String note) {
}
