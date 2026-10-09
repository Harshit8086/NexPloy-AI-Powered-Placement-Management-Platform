package com.nexploy.campuslink.aptitude.controller;

import com.nexploy.campuslink.aptitude.dto.AptitudeAnalysisResponse;
import com.nexploy.campuslink.aptitude.dto.AptitudeTestResponse;
import com.nexploy.campuslink.aptitude.dto.AttemptResponse;
import com.nexploy.campuslink.aptitude.dto.AttemptSummaryResponse;
import com.nexploy.campuslink.aptitude.dto.SaveAnswerRequest;
import com.nexploy.campuslink.aptitude.dto.StartAttemptRequest;
import com.nexploy.campuslink.aptitude.dto.StartPracticeRequest;
import com.nexploy.campuslink.aptitude.dto.StudyPlanResponse;
import com.nexploy.campuslink.aptitude.dto.SubmitAttemptRequest;
import com.nexploy.campuslink.aptitude.service.AptitudeService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service aptitude endpoints for the signed-in student: official bank-drawn tests (which feed
 * placement readiness) and AI practice attempts (which never do). Every action is resolved by
 * email == username (like the rest of {@code /api/students/me/*}) and ownership of each attempt is
 * enforced in {@link AptitudeService}, so a student can only ever see and act on their own attempts.
 */
@RestController
@RequestMapping("/api/students/me/aptitude")
@RequiredArgsConstructor
public class StudentAptitudeController {

    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final AptitudeService aptitudeService;

    /** The five catalog tests with bank availability and the student's last official score each. */
    @GetMapping("/tests")
    public List<AptitudeTestResponse> listTests() {
        return aptitudeService.listTests(currentStudent());
    }

    /** The signed-in student's attempt history (official and practice), newest first. */
    @GetMapping("/history")
    public List<AttemptSummaryResponse> history() {
        return aptitudeService.history(currentStudent());
    }

    /** Starts (or resumes) an official test: bank draw, shuffled snapshot, IN_PROGRESS attempt. */
    @PostMapping("/attempts")
    public AttemptResponse start(@Valid @RequestBody StartAttemptRequest request) {
        return aptitudeService.startOfficialAttempt(currentStudent(), request.testId());
    }

    /** Starts an AI practice session; its questions persist privately and are never bank-drawn. */
    @PostMapping("/practice")
    public AttemptResponse startPractice(@Valid @RequestBody StartPracticeRequest request) {
        return aptitudeService.startPracticeAttempt(currentStudent(), request.category(), request.count());
    }

    /** One attempt in full (owner only; otherwise 404). The answer key appears only after completion. */
    @GetMapping("/attempts/{id}")
    public AttemptResponse get(@PathVariable String id) {
        return aptitudeService.getAttempt(currentStudent(), id);
    }

    /** Persists one answer / mark-for-review change while the attempt is IN_PROGRESS. */
    @PutMapping("/attempts/{id}/answers")
    public AttemptResponse saveAnswer(
        @PathVariable String id, @Valid @RequestBody SaveAnswerRequest request) {
        return aptitudeService.saveAnswer(currentStudent(), id, request);
    }

    /** Finalises the attempt: deterministic scoring, section results, readiness feed if official. */
    @PostMapping("/attempts/{id}/submit")
    public AttemptResponse submit(
        @PathVariable String id, @Valid @RequestBody SubmitAttemptRequest request) {
        return aptitudeService.submitAttempt(currentStudent(), id, request.timeSpentSeconds());
    }

    /** AI performance analysis of a completed attempt (owner only; 409 while still in progress). */
    @GetMapping("/attempts/{id}/analysis")
    public AptitudeAnalysisResponse analysis(@PathVariable String id) {
        return aptitudeService.analyze(currentStudent(), id);
    }

    /** Curated study plan for a completed attempt's weak sections (owner only; 409 in progress). */
    @GetMapping("/attempts/{id}/study-plan")
    public StudyPlanResponse studyPlan(@PathVariable String id) {
        return aptitudeService.studyPlan(currentStudent(), id);
    }

    /** Starts (or resumes) a practice-lane retry of a completed attempt's missed questions. */
    @PostMapping("/attempts/{id}/retry")
    public AttemptResponse retry(@PathVariable String id) {
        return aptitudeService.startRetryAttempt(currentStudent(), id);
    }

    private Student currentStudent() {
        UserAccount user = campusAccessService.currentUser();
        return studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
    }
}
