package com.nexploy.campuslink.offer.exception;

/**
 * Raised when an officer tries to advance an offer to the DOCS stage before the student's
 * documents have all been approved in the Documents module. Maps to 409 CONFLICT.
 */
public class DocumentsNotVerifiedException extends RuntimeException {

    public DocumentsNotVerifiedException(String message) {
        super(message);
    }
}
