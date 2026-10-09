package com.nexploy.campuslink.mentor.exception;

public class StudentNotAssignedException extends RuntimeException {

    public StudentNotAssignedException(String mentorUsername, String studentId) {
        super("Mentor " + mentorUsername + " is not assigned to student " + studentId);
    }
}
