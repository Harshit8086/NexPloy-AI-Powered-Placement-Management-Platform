package com.nexploy.campuslink.student.dto;

import java.time.OffsetDateTime;

/**
 * A single past resume analysis, opened from the Analysis History list. It restores the exact
 * structured {@link ResumeAnalysisResponse} that was produced at the time of the run, alongside the
 * run's metadata (id, resume filename, timestamp), so the UI can re-render every section of a prior
 * analysis without recomputing it.
 */
public record ResumeAnalysisDetail(
    String id,
    String resumeName,
    OffsetDateTime createdAt,
    ResumeAnalysisResponse analysis) {}
