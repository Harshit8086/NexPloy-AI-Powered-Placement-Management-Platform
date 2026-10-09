package com.nexploy.campuslink.mentor.dto;

import com.nexploy.campuslink.mentor.entity.MentorNote;
import java.time.OffsetDateTime;

public record MentorNoteResponse(
    String id,
    String studentId,
    String campusId,
    String authorUsername,
    String note,
    OffsetDateTime createdAt
) {

    public static MentorNoteResponse from(MentorNote note) {
        return new MentorNoteResponse(
            note.getId(),
            note.getStudentId(),
            note.getCampusId(),
            note.getAuthorUsername(),
            note.getNote(),
            note.getCreatedAt()
        );
    }
}
