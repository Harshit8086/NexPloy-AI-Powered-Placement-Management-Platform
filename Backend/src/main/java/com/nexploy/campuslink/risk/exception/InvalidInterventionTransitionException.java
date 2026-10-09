package com.nexploy.campuslink.risk.exception;

/**
 * Raised when an intervention plan is moved through a status transition that is not allowed
 * (the lifecycle is PROPOSED -> ACTIVE -> COMPLETED, no skips or reversals). Maps to HTTP 409.
 */
public class InvalidInterventionTransitionException extends RuntimeException {

    public InvalidInterventionTransitionException(String message) {
        super(message);
    }
}
