package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.MockInterviewStatus;
import com.nexploy.campuslink.interview.entity.MockInterviewType;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Full detail of one practice interview: its metadata, every question (with answers/scores as they
 * are filled in), and — once completed — the overall report. This is the shape returned by start,
 * get-by-id and complete.
 */
public record MockInterviewResponse(
    String id,
    MockInterviewType type,
    String role,
    boolean useResume,
    MockInterviewStatus status,
    int currentQuestion,
    Integer overallScore,
    List<String> strengths,
    List<String> weaknesses,
    List<String> recommendations,
    String summary,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<MockInterviewQuestionResponse> questions
) {
}
