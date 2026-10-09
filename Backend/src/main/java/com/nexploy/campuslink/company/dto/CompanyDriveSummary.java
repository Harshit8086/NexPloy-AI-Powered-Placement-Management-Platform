package com.nexploy.campuslink.company.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single drive a company is running, as summarised on the company profile. Every field is copied
 * straight from the persisted {@code drives} row — nothing is derived or fabricated. Fields the
 * drive does not populate (package, location, deadline) are simply {@code null} here.
 */
public record CompanyDriveSummary(
    String driveId,
    String role,
    String status,
    String location,
    BigDecimal packageLpa,
    LocalDate deadline
) {
}
