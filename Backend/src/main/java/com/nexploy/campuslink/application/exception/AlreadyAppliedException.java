package com.nexploy.campuslink.application.exception;

/** Thrown when a student tries to apply to a drive they have already applied to. Maps to 409. */
public class AlreadyAppliedException extends RuntimeException {
    public AlreadyAppliedException(String driveId) {
        super("You have already applied to drive " + driveId);
    }
}
