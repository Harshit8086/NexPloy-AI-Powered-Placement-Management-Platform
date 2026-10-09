package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.MockInterviewType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/students/me/mock-interviews} — starts a new practice interview.
 * The student picks a type and (optionally) a target role, and whether to ground questions on their
 * stored resume signals.
 */
public record StartMockInterviewRequest(
    @NotNull MockInterviewType type,
    @Size(max = 200) String role,
    boolean useResume
) {
}
