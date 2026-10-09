package com.nexploy.campuslink.student.exception;

/**
 * A student record with this email already exists in the campus directory — raised by the officer
 * create/edit/CSV-import duplicate pre-checks. Maps to 409; the V47 uq_students_campus_email
 * functional index remains the race-condition backstop.
 */
public class StudentAlreadyExistsException extends RuntimeException {
    public StudentAlreadyExistsException(String email) {
        super("A student record with this email already exists: " + email);
    }
}
