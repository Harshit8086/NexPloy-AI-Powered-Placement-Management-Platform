package com.nexploy.campuslink.aptitude.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /attempts — which catalog test to start. */
public record StartAttemptRequest(
    @NotBlank String testId
) {
}
