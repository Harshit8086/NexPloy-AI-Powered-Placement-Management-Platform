package com.nexploy.campuslink.interview.exception;

/**
 * A mock-interview action that is not allowed in the current state (e.g. answering or completing an
 * interview that is already COMPLETED). Mapped to HTTP 409 by the GlobalExceptionHandler.
 */
public class InvalidMockInterviewStateException extends RuntimeException {

    public InvalidMockInterviewStateException(String message) {
        super(message);
    }
}
