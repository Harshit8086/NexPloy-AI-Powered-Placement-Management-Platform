package com.nexploy.campuslink.drive.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Request to fully replace a drive's editable fields (PUT). Identity and audit fields
 * (id, campusId, status, closedAt/closedBy) are never client-modifiable here — status
 * moves only through PATCH /status. recruiterId may be reassigned by an officer.
 */
public record UpdateDriveRequest(
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
