package com.nexploy.campuslink.schedule.exception;

/** A venue id that does not resolve. Mapped to HTTP 404 by the GlobalExceptionHandler. */
public class VenueNotFoundException extends RuntimeException {

    public VenueNotFoundException(String venueId) {
        super("Venue not found: " + venueId);
    }
}
