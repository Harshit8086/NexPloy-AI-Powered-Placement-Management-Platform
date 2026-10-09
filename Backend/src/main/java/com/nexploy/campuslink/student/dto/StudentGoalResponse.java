package com.nexploy.campuslink.student.dto;

import com.nexploy.campuslink.student.entity.StudentGoal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The student's currently applied growth roadmap (Phase 3b). {@code factors} is the deserialized
 * list of selected What-If factor keys; the service supplies it since the entity stores it as JSON.
 */
public record StudentGoalResponse(
    String id,
    List<String> factors,
    String targetRole,
    Integer baselineProbability,
    Integer projectedProbability,
    Integer readinessImpact,
    String recommendedPath,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {

    public static StudentGoalResponse from(StudentGoal goal, List<String> factors) {
        return new StudentGoalResponse(
            goal.getId(),
            factors,
            goal.getTargetRole(),
            goal.getBaselineProbability(),
            goal.getProjectedProbability(),
            goal.getReadinessImpact(),
            goal.getRecommendedPath(),
            goal.getStatus(),
            goal.getCreatedAt(),
            goal.getUpdatedAt()
        );
    }
}
