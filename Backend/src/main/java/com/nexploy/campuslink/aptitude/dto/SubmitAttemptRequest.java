package com.nexploy.campuslink.aptitude.dto;

import jakarta.validation.constraints.Min;

/**
 * Body of POST /attempts/{id}/submit. {@code timeSpentSeconds} is client-reported (the timer runs
 * in the browser) and is clamped server-side to the duration limit plus a small grace window.
 */
public record SubmitAttemptRequest(
    @Min(0) int timeSpentSeconds
) {
}
