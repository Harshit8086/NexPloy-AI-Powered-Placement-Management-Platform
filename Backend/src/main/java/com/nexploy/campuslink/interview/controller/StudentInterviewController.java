package com.nexploy.campuslink.interview.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.interview.dto.InterviewResponse;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A student's interview slots, campus-scoped. Backs the student Interviews/Assessments screen. */
@RestController
@RequestMapping("/api/students/{studentId}/interviews")
@RequiredArgsConstructor
public class StudentInterviewController {

    private final InterviewRepository interviewRepository;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<InterviewResponse> listForStudent(@PathVariable String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return interviewRepository.findByStudentId(studentId).stream().map(InterviewResponse::from).toList();
    }
}
