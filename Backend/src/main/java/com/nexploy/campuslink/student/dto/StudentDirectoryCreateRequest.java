package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Officer directory entry (V60): one unclaimed student record in the campus directory. No account
 * is created — the row waits with {@code source=DIRECTORY} until the student registers with the
 * matching email and claims it. Email + name + campus are the required identity; the academic
 * fields seed the profile the student sees at claim time.
 */
public record StudentDirectoryCreateRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Email @Size(max = 320) String email,
    @Size(max = 100) String rollNumber,
    @NotBlank String campusId,
    @Size(max = 120) String branch,
    @Size(max = 32) String branchCode,
    @Size(max = 120) String department,
    @Size(max = 60) String batch,
    @Min(1) @Max(12) Integer semester,
    @Min(1900) @Max(2100) Integer graduationYear,
    @Size(max = 60) String yearLabel,
    @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal cgpa,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal tenthPercentage,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal twelfthPercentage,
    @Min(0) Integer backlogs,
    @Size(max = 32) String phone
) {
}
