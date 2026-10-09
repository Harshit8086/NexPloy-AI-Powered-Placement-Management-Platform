package com.nexploy.campuslink.application.exception;

/**
 * Thrown when a student tries to apply to a drive that is not open — either its status is not
 * ACTIVE or its application deadline has passed. Maps to 409.
 */
public class DriveClosedException extends RuntimeException {
    public DriveClosedException(String message) {
        super(message);
    }
}
