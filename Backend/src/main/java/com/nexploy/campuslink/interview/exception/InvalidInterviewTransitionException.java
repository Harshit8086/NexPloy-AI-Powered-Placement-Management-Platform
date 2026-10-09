package com.nexploy.campuslink.interview.exception;

/**
 * An interview state change that is not allowed (e.g. rescheduling a COMPLETED or CANCELLED
 * interview). Mapped to HTTP 409 by the GlobalExceptionHandler.
 */
public class InvalidInterviewTransitionException extends RuntimeException {

    public InvalidInterviewTransitionException(String message) {
        super(message);
    }
}
