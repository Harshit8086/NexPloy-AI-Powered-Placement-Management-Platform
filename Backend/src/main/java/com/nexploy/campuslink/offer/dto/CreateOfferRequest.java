package com.nexploy.campuslink.offer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to extend a new offer. The core fields are required; the display/tracking fields
 * (offer type, location, joining date, acceptance deadline, company name, PPO source) are
 * optional and default sensibly when omitted, so existing callers keep working.
 */
public record CreateOfferRequest(
    @NotBlank String studentId,
    @NotBlank String driveId,
    @NotBlank String recruiterId,
    @NotBlank String role,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal packageLpa,
    String companyName,
    String offerType,
    String location,
    LocalDate joiningDate,
    LocalDate acceptanceDeadline,
    String fromInternship
) {
}
