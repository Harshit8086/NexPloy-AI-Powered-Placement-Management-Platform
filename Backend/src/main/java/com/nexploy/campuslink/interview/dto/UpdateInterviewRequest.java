package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.InterviewMode;
import java.time.OffsetDateTime;

/**
 * Request to reschedule an interview (PATCH). Every field is optional; only the non-null
 * fields are applied, leaving the rest unchanged. A cancelled interview cannot be rescheduled.
 */
public record UpdateInterviewRequest(
    OffsetDateTime scheduledAt,
    InterviewMode mode,
    String round,
    String venue,
    String panel
) {
}
