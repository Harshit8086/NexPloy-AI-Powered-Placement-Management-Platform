package com.nexploy.campuslink.student.exception;

public class StudentSkillNotFoundException extends RuntimeException {

    public StudentSkillNotFoundException(String skillId) {
        super("Skill not found: " + skillId);
    }
}
