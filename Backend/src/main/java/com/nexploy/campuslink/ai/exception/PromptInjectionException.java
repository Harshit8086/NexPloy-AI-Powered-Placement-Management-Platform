package com.nexploy.campuslink.ai.exception;

/**
 * Raised when untrusted text (a resume or job description) contains content that looks like an
 * instruction to the model rather than data to extract, per SECURITY.md section 6. The text is
 * rejected before it ever reaches the extraction boundary.
 */
public class PromptInjectionException extends RuntimeException {

    public PromptInjectionException(String detail) {
        super("Input rejected as a possible prompt-injection attempt: " + detail);
    }
}
