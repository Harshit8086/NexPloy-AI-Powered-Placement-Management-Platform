package com.nexploy.campuslink.schedule.exception;

/** A panel id that does not resolve. Mapped to HTTP 404 by the GlobalExceptionHandler. */
public class PanelNotFoundException extends RuntimeException {

    public PanelNotFoundException(String panelId) {
        super("Panel not found: " + panelId);
    }
}
