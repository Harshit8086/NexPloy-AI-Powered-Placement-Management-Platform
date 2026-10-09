package com.nexploy.campuslink.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reason an officer/admin gives when rejecting an optimization proposal. Recorded for audit
 * (SECURITY §4); the active schedule is left unchanged.
 */
public record RejectRunRequest(
    @NotBlank @Size(max = 500) String reason
) {
}
