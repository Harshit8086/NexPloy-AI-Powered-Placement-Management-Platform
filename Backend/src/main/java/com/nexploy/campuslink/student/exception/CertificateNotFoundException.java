package com.nexploy.campuslink.student.exception;

public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(String certificateId) {
        super("Certificate not found: " + certificateId);
    }
}
