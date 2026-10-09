package com.nexploy.campuslink.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to replace a venue's editable fields (PUT). active moves via PATCH /active. */
public record UpdateVenueRequest(
    @NotBlank @Size(max = 200) String name,
    @Min(1) Integer capacity
) {
}
