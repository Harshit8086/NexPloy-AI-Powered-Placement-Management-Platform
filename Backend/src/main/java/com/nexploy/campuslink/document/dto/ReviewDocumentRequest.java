package com.nexploy.campuslink.document.dto;

import com.nexploy.campuslink.document.entity.ReviewStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ReviewDocumentRequest(
    @NotNull ReviewStatus decision
) {

    @AssertTrue(message = "Review decision must be APPROVED or REJECTED")
    public boolean isTerminalDecision() {
        return decision == null || decision == ReviewStatus.APPROVED || decision == ReviewStatus.REJECTED;
    }
}
