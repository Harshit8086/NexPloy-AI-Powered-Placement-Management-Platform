package com.nexploy.campuslink.aptitude.exception;

/** Unknown catalog test id requested. */
public class AptitudeTestNotFoundException extends RuntimeException {

    public AptitudeTestNotFoundException(String message) {
        super(message);
    }
}
