package com.nexploy.campuslink.schedule.exception;

/**
 * Raised when an optimization run is approved or rejected from a status that does not allow it
 * (only a PENDING run can transition). Maps to HTTP 409.
 */
public class InvalidOptimizationRunTransitionException extends RuntimeException {

    public InvalidOptimizationRunTransitionException(String message) {
        super(message);
    }
}
