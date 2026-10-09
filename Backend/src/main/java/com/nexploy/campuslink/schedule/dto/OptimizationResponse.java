package com.nexploy.campuslink.schedule.dto;

import com.nexploy.campuslink.schedule.client.OrToolsOptimizationClient.OptimizationAlternative;
import java.util.List;

public record OptimizationResponse(
    String runId,
    String status,
    String scheduleVersion,
    int hardViolations,
    int softScore,
    List<OptimizationAlternative> alternatives,
    List<OptimizationAlternative> selectedAssignments
) {
}
