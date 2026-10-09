package com.nexploy.campuslink.interview.exception;

public class MockInterviewNotFoundException extends RuntimeException {

    public MockInterviewNotFoundException(String mockInterviewId) {
        super("Mock interview not found: " + mockInterviewId);
    }
}
