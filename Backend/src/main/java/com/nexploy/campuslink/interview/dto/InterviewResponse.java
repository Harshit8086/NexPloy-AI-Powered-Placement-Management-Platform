package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewMode;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import java.time.OffsetDateTime;

public record InterviewResponse(
    String id,
    String campusId,
    String studentId,
    String driveId,
    String scheduleEventId,
    String companyName,
    String round,
    String venue,
    String panel,
    InterviewMode mode,
    OffsetDateTime scheduledAt,
    InterviewStatus status,
    InterviewResult result,
    String feedback
) {

    public static InterviewResponse from(Interview interview) {
        return new InterviewResponse(
            interview.getId(),
            interview.getCampusId(),
            interview.getStudentId(),
            interview.getDriveId(),
            interview.getScheduleEventId(),
            interview.getCompanyName(),
            interview.getRound(),
            interview.getVenue(),
            interview.getPanel(),
            interview.getMode(),
            interview.getScheduledAt(),
            interview.getStatus(),
            interview.getResult(),
            interview.getFeedback()
        );
    }
}
