package com.nexploy.campuslink.application.exception;

/**
 * An application status change the placement funnel does not allow (e.g. JOINED -> APPLIED).
 * Mapped to HTTP 409 by the GlobalExceptionHandler.
 */
public class InvalidApplicationTransitionException extends RuntimeException {

    public InvalidApplicationTransitionException(String message) {
        super(message);
    }
}
