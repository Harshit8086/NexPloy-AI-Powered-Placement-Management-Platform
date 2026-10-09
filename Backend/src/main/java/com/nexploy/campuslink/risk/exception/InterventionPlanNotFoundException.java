package com.nexploy.campuslink.risk.exception;

/**
 * Raised when an intervention plan id does not exist. Maps to HTTP 404.
 */
public class InterventionPlanNotFoundException extends RuntimeException {

    public InterventionPlanNotFoundException(String planId) {
        super("Intervention plan not found: " + planId);
    }
}
