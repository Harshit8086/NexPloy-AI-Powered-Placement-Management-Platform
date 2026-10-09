package com.nexploy.campuslink.interview.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.interview.dto.MockInterviewResponse;
import com.nexploy.campuslink.interview.dto.MockInterviewSummaryResponse;
import com.nexploy.campuslink.interview.dto.StartMockInterviewRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerRequest;
import com.nexploy.campuslink.interview.dto.SubmitAnswerResponse;
import com.nexploy.campuslink.interview.service.MockInterviewService;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service AI mock (practice) interview endpoints for the signed-in student. Every action is
 * resolved by email == username (like the rest of {@code /api/students/me/*}) and ownership of each
 * run is enforced in {@link MockInterviewService}, so a student can only ever see and act on their
 * own practice interviews. This is a NEW capability, entirely separate from the placement interview
 * endpoints under {@code /api/interviews}.
 */
@RestController
@RequestMapping("/api/students/me/mock-interviews")
@RequiredArgsConstructor
public class StudentMockInterviewController {

    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final MockInterviewService mockInterviewService;

    /** Starts a new practice interview and returns it with its generated questions. */
    @PostMapping
    public MockInterviewResponse start(@Valid @RequestBody StartMockInterviewRequest request) {
        return mockInterviewService.start(currentStudent(), request);
    }

    /** The signed-in student's practice-interview history, newest first. */
    @GetMapping
    public List<MockInterviewSummaryResponse> list() {
        return mockInterviewService.list(currentStudent());
    }

    /** One practice interview in full (owner only; otherwise 404). */
    @GetMapping("/{id}")
    public MockInterviewResponse get(@PathVariable String id) {
        return mockInterviewService.get(currentStudent(), id);
    }

    /** Submits the answer to the current question and returns its scoring detail. */
    @PostMapping("/{id}/answers")
    public SubmitAnswerResponse submitAnswer(
        @PathVariable String id, @Valid @RequestBody SubmitAnswerRequest request) {
        return mockInterviewService.submitAnswer(currentStudent(), id, request);
    }

    /** Finalises the interview and returns the full report. */
    @PostMapping("/{id}/complete")
    public MockInterviewResponse complete(@PathVariable String id) {
        return mockInterviewService.complete(currentStudent(), id);
    }

    private Student currentStudent() {
        UserAccount user = campusAccessService.currentUser();
        return studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
    }
}
