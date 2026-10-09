package com.nexploy.campuslink.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Self-service recruiter registration — identifies the person and their company. The work email
 * doubles as the login identity (the {@code user_accounts} primary key) and the recruiter record's
 * contact email. campusId binds the recruiter to the campus they recruit for; this system scopes
 * every recruiter to a campus, so it is required even though it is not a "company" attribute.
 * Company website is optional. The confirm-password check is client-side and never sent here.
 */
public record RecruiterRegisterRequest(
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 32) String phone,
    @NotBlank @Size(min = 8, max = 100) String password,
    @NotBlank String campusId,
    @NotBlank @Size(max = 200) String companyName,
    @Size(max = 320) String companyWebsite,
    @NotBlank @Size(max = 120) String companyIndustry,
    @NotBlank @Size(max = 200) String companyLocation,
    @NotBlank @Size(max = 120) String designation
) {
}
