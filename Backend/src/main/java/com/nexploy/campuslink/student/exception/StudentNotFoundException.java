package com.nexploy.campuslink.student.exception;

import com.nexploy.campuslink.student.entity.Student;

public class StudentNotFoundException extends RuntimeException {

    public StudentNotFoundException(String studentId) {
        super("Student not found: " + studentId);
    }
}

