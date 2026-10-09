package com.nexploy.campuslink.student.dto;

import com.nexploy.campuslink.student.entity.StudentBacklogHistory;
import java.time.OffsetDateTime;

/** One entry in a student's backlog timeline, as returned to the profile UI. */
public record BacklogHistoryEntryResponse(
    String id,
    Integer semester,
    Integer activeBacklogs,
    Integer clearedBacklogs,
    String note,
    String recordedBy,
    OffsetDateTime recordedAt) {

    public static BacklogHistoryEntryResponse from(StudentBacklogHistory h) {
        return new BacklogHistoryEntryResponse(
            h.getId(), h.getSemester(), h.getActiveBacklogs(), h.getClearedBacklogs(),
            h.getNote(), h.getRecordedBy(), h.getRecordedAt());
    }
}
