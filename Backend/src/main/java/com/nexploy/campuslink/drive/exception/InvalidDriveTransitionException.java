package com.nexploy.campuslink.drive.exception;

/**
 * A drive status change that the lifecycle does not allow (e.g. COMPLETED -> DRAFT).
 * Mapped to HTTP 409 by the GlobalExceptionHandler, mirroring InvalidOfferTransitionException.
 */
public class InvalidDriveTransitionException extends RuntimeException {

    public InvalidDriveTransitionException(String message) {
        super(message);
    }
}
