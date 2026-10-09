package com.nexploy.campuslink.drive.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request to create a new placement drive. The core identity fields are required; the
 * eligibility floors and enrichment fields are optional and default sensibly. The list
 * fields (requiredSkills / rounds / allowedBranches) are serialized to the drive's JSON
 * text columns by {@code DriveService}. campusId is derived server-side from the actor,
 * and status always starts at DRAFT — neither is client-supplied (no privilege escalation).
 */
public record CreateDriveRequest(
    @NotBlank String recruiterId,
    @NotBlank String companyName,
    @NotBlank String role,
    String location,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal packageLpa,
    String cycleId,
    BigDecimal minimumCgpa,
    Integer maximumBacklogs,
    Integer graduationYear,
    LocalDate deadline,
    String driveType,
    String description,
    Integer expectedHiring,
    String venue,
    BigDecimal minimumTenthPercentage,
    BigDecimal minimumTwelfthPercentage,
    List<String> requiredSkills,
    List<DriveRound> rounds,
    List<String> allowedBranches
) {
}
