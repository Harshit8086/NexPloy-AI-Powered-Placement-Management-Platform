package com.nexploy.campuslink.aptitude.exception;

/** Bank question not found (officer bank operations). */
public class AptitudeQuestionNotFoundException extends RuntimeException {

    public AptitudeQuestionNotFoundException(String message) {
        super(message);
    }
}
