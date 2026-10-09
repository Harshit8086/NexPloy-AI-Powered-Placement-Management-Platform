package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.InterviewMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request to schedule a new interview slot. Student and drive are required and must exist;
 * the interview's campus is derived from the drive (never client-supplied). Display fields
 * (round/venue/panel) and an optional scheduleEventId are carried through unchanged.
 */
public record ScheduleInterviewRequest(
    @NotBlank String studentId,
    @NotBlank String driveId,
    @NotNull InterviewMode mode,
    @NotNull OffsetDateTime scheduledAt,
    String round,
    String venue,
    String panel,
    String scheduleEventId,
    @Size(max = 200) String companyName
) {
}
