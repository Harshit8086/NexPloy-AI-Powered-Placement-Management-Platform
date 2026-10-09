package com.nexploy.campuslink.drive.dto;

import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Frontend-facing view of a drive (camelCase). The scalar eligibility floors
 * (minimumCgpa/maximumBacklogs/graduationYear/minimumTenth/TwelfthPercentage) and
 * allowedBranches together give the frontend everything it needs to compose its
 * EligibilityCriteria; requiredSkills and rounds back the drive detail + matching
 * screens. List fields are parsed from the stored JSON columns by {@code DriveService}.
 */
public record DriveResponse(
    String id,
    String campusId,
    String recruiterId,
    String companyName,
    String role,
    String location,
    BigDecimal packageLpa,
    BigDecimal minimumCgpa,
    Integer maximumBacklogs,
    Integer graduationYear,
    LocalDate deadline,
    DriveStatus status,
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

    /**
     * Builds the response from the entity plus the parsed JSON list fields. The caller
     * (DriveService) owns JSON parsing, keeping this DTO free of serialization concerns.
     */
    public static DriveResponse from(
        Drive drive,
        List<String> requiredSkills,
        List<DriveRound> rounds,
        List<String> allowedBranches
    ) {
        return new DriveResponse(
            drive.getId(),
            drive.getCampusId(),
            drive.getRecruiterId(),
            drive.getCompanyName(),
            drive.getRole(),
            drive.getLocation(),
            drive.getPackageLpa(),
            drive.getMinimumCgpa(),
            drive.getMaximumBacklogs(),
            drive.getGraduationYear(),
            drive.getDeadline(),
            drive.getStatus(),
            drive.getDriveType(),
            drive.getDescription(),
            drive.getExpectedHiring(),
            drive.getVenue(),
            drive.getMinimumTenthPercentage(),
            drive.getMinimumTwelfthPercentage(),
            requiredSkills,
            rounds,
            allowedBranches
        );
    }
}
