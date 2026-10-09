package com.nexploy.campuslink.student.exception;

/** Raised when a student requests a resume-analysis history entry that does not exist or is not theirs. */
public class ResumeAnalysisNotFoundException extends RuntimeException {

    public ResumeAnalysisNotFoundException(String id) {
        super("Resume analysis not found: " + id);
    }
}
