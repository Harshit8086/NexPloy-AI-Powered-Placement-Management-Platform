package com.nexploy.campuslink.student.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Officer directory edit (PUT /api/students/{id}/directory, V60). Same fields as the create
 * request, all optional (null = unchanged). Email may be changed only while the record is still
 * unclaimed — once a student registers against it, the email doubles as the account username and
 * is immutable (a change attempt yields 409).
 */
public record StudentDirectoryUpdateRequest(
    @Size(max = 200) String name,
    @Email @Size(max = 320) String email,
    @Size(max = 100) String rollNumber,
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
