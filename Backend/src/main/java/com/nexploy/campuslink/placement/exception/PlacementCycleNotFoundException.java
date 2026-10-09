package com.nexploy.campuslink.placement.exception;

/** A placement cycle id that does not resolve. Mapped to HTTP 404 by the GlobalExceptionHandler. */
public class PlacementCycleNotFoundException extends RuntimeException {

    public PlacementCycleNotFoundException(String cycleId) {
        super("Placement cycle not found: " + cycleId);
    }
}
