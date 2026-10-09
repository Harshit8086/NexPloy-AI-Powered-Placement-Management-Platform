package com.nexploy.campuslink.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A free-text question asked of the NEXPLOY Copilot. The role and identity that ground the answer are
 * resolved server-side from the authenticated user, never from the request body — a client cannot ask
 * "as" someone else. The question is the only untrusted input and is screened for prompt injection.
 */
public record CopilotRequest(
    @NotBlank @Size(max = 1000) String question
) {
}
