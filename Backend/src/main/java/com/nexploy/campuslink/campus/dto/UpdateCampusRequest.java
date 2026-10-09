package com.nexploy.campuslink.campus.dto;

import jakarta.validation.constraints.NotBlank;

/** Request to replace a campus's editable fields (PUT). active moves via PATCH /active. */
public record UpdateCampusRequest(
    @NotBlank String name,
    String city,
    String state
) {
}
