package com.nexploy.campuslink.document.exception;

/**
 * The document row exists but no file bytes were ever attached — a legacy metadata-only
 * registration. The file endpoint maps this to 404; the student must re-upload via the
 * attach-file endpoint.
 */
public class DocumentFileMissingException extends RuntimeException {

    public DocumentFileMissingException(String documentId) {
        super("No file is attached to document: " + documentId);
    }
}
