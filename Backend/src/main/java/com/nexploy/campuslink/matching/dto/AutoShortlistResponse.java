package com.nexploy.campuslink.matching.dto;

import java.util.List;

/**
 * Summary of a batch auto-shortlist run. The counts partition the considered pool into the three
 * mutually exclusive outcomes, and {@code outcomes} gives the per-student detail in fit order so an
 * officer can see exactly who was shortlisted and why the rest were skipped. {@code minFit} echoes
 * the effective threshold that was applied.
 */
public record AutoShortlistResponse(
    String driveId,
    int minFit,
    int shortlisted,
    int skippedIneligible,
    int skippedLowFit,
    List<Outcome> outcomes
) {
    /**
     * One student's outcome. {@code result} is SHORTLISTED, SKIPPED_INELIGIBLE or SKIPPED_LOW_FIT;
     * {@code eligible} is the server-side deterministic eligibility verdict re-checked during the run.
     */
    public record Outcome(String studentId, String result, int overallFit, boolean eligible) {
    }
}
