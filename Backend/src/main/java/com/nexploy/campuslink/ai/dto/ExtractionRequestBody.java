package com.nexploy.campuslink.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Untrusted free text to extract from (a resume or a job description). Size-capped to bound the
 * work and screened for prompt injection before extraction. The text is treated strictly as data.
 */
public record ExtractionRequestBody(
    @NotBlank @Size(max = 20_000) String text
) {
}
