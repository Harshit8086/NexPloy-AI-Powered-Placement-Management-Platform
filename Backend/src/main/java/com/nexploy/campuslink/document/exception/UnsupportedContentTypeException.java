package com.nexploy.campuslink.document.exception;

public class UnsupportedContentTypeException extends RuntimeException {

    public UnsupportedContentTypeException(String contentType) {
        super("Unsupported document content type: " + contentType);
    }
}
