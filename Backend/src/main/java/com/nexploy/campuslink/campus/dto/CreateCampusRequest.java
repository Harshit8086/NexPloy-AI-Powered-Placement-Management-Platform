package com.nexploy.campuslink.campus.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create a campus. The id is client-supplied (app-assigned varchar PK convention,
 * e.g. "campus_iitb"); name is required, location fields optional. New campuses start active.
 */
public record CreateCampusRequest(
    @NotBlank String id,
    @NotBlank String name,
    String city,
    String state
) {
}
