package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.student.dto.ApplyRoadmapRequest;
import com.nexploy.campuslink.student.dto.StudentGoalResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentGoal;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentGoalRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service management of the signed-in student's applied growth roadmap (Phase 3b). The student
 * is resolved by email (== username), so a student only ever reads or mutates their own goal. There
 * is one active roadmap per student — applying re-applies in place — and the selected What-If
 * factors are persisted as JSON so the applied roadmap survives refresh/login.
 *
 * <p>This never touches the authoritative readiness/assessment inputs; it only records the
 * student's chosen intent.
 */
@Service
@RequiredArgsConstructor
public class StudentGoalService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final StudentRepository studentRepository;
    private final StudentGoalRepository studentGoalRepository;
    private final CampusAccessService campusAccessService;
    private final ObjectMapper objectMapper;

    /** The student's currently applied roadmap, or empty if they have not applied one yet. */
    @Transactional(readOnly = true)
    public Optional<StudentGoalResponse> getOwnGoal() {
        Student student = currentStudent();
        return studentGoalRepository.findByStudentId(student.getId()).map(this::toResponse);
    }

    /** Applies (or re-applies) the student's roadmap from the What-If Simulator, upserting in place. */
    @Transactional
    public StudentGoalResponse applyOwnRoadmap(ApplyRoadmapRequest request) {
        Student student = currentStudent();
        OffsetDateTime now = OffsetDateTime.now();

        StudentGoal goal = studentGoalRepository.findByStudentId(student.getId())
            .orElseGet(() -> StudentGoal.builder()
                .id("goal_" + UUID.randomUUID())
                .studentId(student.getId())
                .campusId(student.getCampusId())
                .createdAt(now)
                .build());

        goal.setFactorsJson(writeValue(request.factors()));
        goal.setTargetRole(student.getTargetRole());
        goal.setBaselineProbability(request.baselineProbability());
        goal.setProjectedProbability(request.projectedProbability());
        goal.setReadinessImpact(request.readinessImpact());
        goal.setRecommendedPath(request.recommendedPath());
        goal.setStatus(STATUS_ACTIVE);
        goal.setUpdatedAt(now);

        return toResponse(studentGoalRepository.save(goal));
    }

    private StudentGoalResponse toResponse(StudentGoal goal) {
        List<String> factors = readValue(
            goal.getFactorsJson(), new TypeReference<List<String>>() {}, List.of());
        return StudentGoalResponse.from(goal, factors);
    }

    /** Resolves the signed-in student's own record (username == email for STUDENT accounts). */
    private Student currentStudent() {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findByEmail(user.getUsername())
            .orElseThrow(() -> new StudentNotFoundException(user.getUsername()));
        campusAccessService.requireAccess(user, student.getCampusId());
        return student;
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not write student roadmap field", exception);
        }
    }

    private <T> T readValue(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read student roadmap field", exception);
        }
    }
}
