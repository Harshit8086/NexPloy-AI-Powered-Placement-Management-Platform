package com.nexploy.campuslink.student.dto;

import java.time.OffsetDateTime;

/**
 * A single entry in a student's resume Analysis History list (newest first). It carries only what the
 * list needs to render and to plot progress over time — the run's id, the resume's filename, its
 * headline score and when it ran. The full structured analysis is fetched on demand via the detail
 * endpoint ({@link ResumeAnalysisDetail}).
 */
public record ResumeAnalysisSummary(
    String id,
    String resumeName,
    Integer overallScore,
    OffsetDateTime createdAt) {}
