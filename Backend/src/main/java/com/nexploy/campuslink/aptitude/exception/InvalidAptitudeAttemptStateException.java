package com.nexploy.campuslink.aptitude.exception;

/** Attempt is in the wrong state for the operation — re-submit, editing a completed attempt, etc. */
public class InvalidAptitudeAttemptStateException extends RuntimeException {

    public InvalidAptitudeAttemptStateException(String message) {
        super(message);
    }
}
