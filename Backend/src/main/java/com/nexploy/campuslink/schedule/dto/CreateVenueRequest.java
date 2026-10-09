package com.nexploy.campuslink.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create an interview venue. Name required; capacity optional (positive when present).
 * campusId is optional — officers/admins may target any campus; when blank the caller's campus is
 * used. New venues start active. id is server-assigned.
 */
public record CreateVenueRequest(
    @NotBlank @Size(max = 200) String name,
    @Min(1) Integer capacity,
    @Size(max = 100) String campusId
) {
}
