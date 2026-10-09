package com.nexploy.campuslink.student.controller;

import com.nexploy.campuslink.student.dto.StudentSkillResponse;
import com.nexploy.campuslink.student.service.StudentSkillService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only skill lookup for placement staff. Unlike {@link StudentSkillController} (which is
 * self-only, keyed on the signed-in student), this exposes a given student's skill set to an
 * officer/admin/mentor within campus reach — the officer Student Directory drawer reads it so the
 * "Skills" section reflects the student's actual recorded skills, not just resume-derived strengths.
 * Authorization (staff role + campus scope) is enforced in {@link StudentSkillService}.
 */
@RestController
@RequestMapping("/api/students/{studentId}/skills")
@RequiredArgsConstructor
public class StudentSkillDirectoryController {

    private final StudentSkillService studentSkillService;

    @GetMapping
    public List<StudentSkillResponse> listSkills(@PathVariable String studentId) {
        return studentSkillService.listSkillsForStudent(studentId).stream()
            .map(StudentSkillResponse::from).toList();
    }
}
