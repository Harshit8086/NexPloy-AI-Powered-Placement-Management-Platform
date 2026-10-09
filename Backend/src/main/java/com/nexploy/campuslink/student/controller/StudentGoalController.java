package com.nexploy.campuslink.student.controller;

import com.nexploy.campuslink.student.dto.ApplyRoadmapRequest;
import com.nexploy.campuslink.student.dto.StudentGoalResponse;
import com.nexploy.campuslink.student.service.StudentGoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service growth-roadmap management for the signed-in student (Phase 3b). All operations
 * resolve the student by email (== username), so a student can only ever read or change their own
 * applied roadmap.
 */
@RestController
@RequestMapping("/api/students/me/goals")
@RequiredArgsConstructor
public class StudentGoalController {

    private final StudentGoalService studentGoalService;

    /** The student's currently applied roadmap; 204 when they have not applied one yet. */
    @GetMapping
    public ResponseEntity<StudentGoalResponse> getGoal() {
        return studentGoalService.getOwnGoal()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Applies (or re-applies) the roadmap from the What-If Simulator, replacing any existing goal. */
    @PutMapping
    public StudentGoalResponse applyRoadmap(@Valid @RequestBody ApplyRoadmapRequest request) {
        return studentGoalService.applyOwnRoadmap(request);
    }
}
