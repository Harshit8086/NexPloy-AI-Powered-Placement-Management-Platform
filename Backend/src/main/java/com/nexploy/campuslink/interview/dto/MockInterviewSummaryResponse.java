package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.MockInterview;
import com.nexploy.campuslink.interview.entity.MockInterviewStatus;
import com.nexploy.campuslink.interview.entity.MockInterviewType;
import java.time.OffsetDateTime;

/**
 * Lightweight row for the practice-interview history list — enough to render the list and open a run,
 * without loading its questions.
 */
public record MockInterviewSummaryResponse(
    String id,
    MockInterviewType type,
    String role,
    MockInterviewStatus status,
    Integer overallScore,
    OffsetDateTime createdAt
) {

    public static MockInterviewSummaryResponse from(MockInterview m) {
        return new MockInterviewSummaryResponse(
            m.getId(), m.getType(), m.getRole(), m.getStatus(), m.getOverallScore(), m.getCreatedAt());
    }
}
