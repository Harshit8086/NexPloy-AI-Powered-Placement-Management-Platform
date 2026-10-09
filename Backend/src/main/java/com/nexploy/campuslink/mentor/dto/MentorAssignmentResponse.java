package com.nexploy.campuslink.mentor.dto;

import com.nexploy.campuslink.mentor.entity.MentorAssignment;
import java.time.OffsetDateTime;

public record MentorAssignmentResponse(
    String id,
    String mentorUsername,
    String studentId,
    String campusId,
    OffsetDateTime assignedAt
) {

    public static MentorAssignmentResponse from(MentorAssignment assignment) {
        return new MentorAssignmentResponse(
            assignment.getId(),
            assignment.getMentorUsername(),
            assignment.getStudentId(),
            assignment.getCampusId(),
            assignment.getAssignedAt()
        );
    }
}
