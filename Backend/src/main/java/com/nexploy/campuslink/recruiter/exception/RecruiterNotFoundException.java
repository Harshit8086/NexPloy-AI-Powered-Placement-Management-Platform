package com.nexploy.campuslink.recruiter.exception;

/** A recruiter id that does not resolve. Mapped to HTTP 404 by the GlobalExceptionHandler. */
public class RecruiterNotFoundException extends RuntimeException {

    public RecruiterNotFoundException(String recruiterId) {
        super("Recruiter not found: " + recruiterId);
    }
}
