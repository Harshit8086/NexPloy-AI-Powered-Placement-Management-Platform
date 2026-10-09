package com.nexploy.campuslink.interview.exception;

public class InterviewNotFoundException extends RuntimeException {

    public InterviewNotFoundException(String interviewId) {
        super("Interview not found: " + interviewId);
    }
}
