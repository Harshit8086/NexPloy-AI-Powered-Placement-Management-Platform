package com.nexploy.campuslink.application.exception;

import java.util.List;

/**
 * Thrown when a student does not meet a drive's eligibility criteria. Carries the specific
 * failing checks so the client can show why. Maps to 409.
 */
public class NotEligibleException extends RuntimeException {
    public NotEligibleException(List<String> failures) {
        super("You do not meet the eligibility criteria: " + String.join("; ", failures));
    }
}
