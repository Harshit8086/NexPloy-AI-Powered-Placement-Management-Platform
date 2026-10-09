package com.nexploy.campuslink.campus.exception;

/** A campus id that does not resolve. Mapped to HTTP 404 by the GlobalExceptionHandler. */
public class CampusNotFoundException extends RuntimeException {

    public CampusNotFoundException(String campusId) {
        super("Campus not found: " + campusId);
    }
}
