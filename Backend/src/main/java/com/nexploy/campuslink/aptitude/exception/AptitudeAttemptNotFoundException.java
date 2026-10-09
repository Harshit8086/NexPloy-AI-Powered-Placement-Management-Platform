package com.nexploy.campuslink.aptitude.exception;

/**
 * Attempt missing OR owned by another student — both read as 404 so an attempt id cannot be
 * probed across accounts.
 */
public class AptitudeAttemptNotFoundException extends RuntimeException {

    public AptitudeAttemptNotFoundException(String message) {
        super(message);
    }
}
