package com.nexploy.campuslink.aptitude.exception;

/** The bank does not hold enough active questions to draw the requested test. */
public class InsufficientAptitudeBankException extends RuntimeException {

    public InsufficientAptitudeBankException(String message) {
        super(message);
    }
}
