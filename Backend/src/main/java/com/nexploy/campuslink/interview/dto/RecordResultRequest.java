package com.nexploy.campuslink.interview.dto;

import com.nexploy.campuslink.interview.entity.InterviewResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordResultRequest(
    @NotNull InterviewResult result,
    @Size(max = 2000) String feedback
) {
}
