package com.nexploy.campuslink.placement.exception;

/** A placement cycle status change the lifecycle does not allow. Mapped to HTTP 409. */
public class InvalidCycleTransitionException extends RuntimeException {

    public InvalidCycleTransitionException(String message) {
        super(message);
    }
}
