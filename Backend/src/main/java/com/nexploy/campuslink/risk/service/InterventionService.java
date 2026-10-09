package com.nexploy.campuslink.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.risk.dto.CreateInterventionRequest;
import com.nexploy.campuslink.risk.dto.InterventionAction;
import com.nexploy.campuslink.risk.dto.InterventionPlanResponse;
import com.nexploy.campuslink.risk.dto.ToggleActionRequest;
import com.nexploy.campuslink.risk.entity.InterventionPlan;
import com.nexploy.campuslink.risk.exception.InterventionPlanNotFoundException;
import com.nexploy.campuslink.risk.exception.InvalidInterventionTransitionException;
import com.nexploy.campuslink.risk.event.InterventionAssignedEvent;
import com.nexploy.campuslink.risk.repository.InterventionPlanRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persisted risk -> intervention workflow (PRD/DESIGN — the `InterventionPlan` contract entity
 * and the `InterventionAssigned` event). An officer/admin raises a plan for an at-risk student and
 * names the responsible mentor; the plan then moves PROPOSED -> ACTIVE -> COMPLETED, with action
 * items checked off and progress derived from them. Access: officers/admins act on any plan in a
 * campus they can reach; the named mentor acts only on their own plans; everyone else is denied.
 */
@Service
@RequiredArgsConstructor
public class InterventionService {

    private final InterventionPlanRepository interventionPlanRepository;
    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InterventionPlanResponse create(String studentId, CreateInterventionRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());

        List<InterventionAction> actions = request.actionLabels().stream()
            .map(label -> new InterventionAction("act_" + UUID.randomUUID(), label, false))
            .toList();

        OffsetDateTime now = OffsetDateTime.now();
        InterventionPlan plan = InterventionPlan.builder()
            .id("ivp_" + UUID.randomUUID())
            .studentId(studentId)
            .campusId(student.getCampusId())
            .mentorUsername(request.mentorUsername())
            .riskEntryId(request.riskEntryId())
            .status("PROPOSED")
            .focusAreasJson(write(request.focusAreas()))
            .actionsJson(write(actions))
            .progress(0)
            .notes(request.notes())
            .createdBy(user.getUsername())
            .createdAt(now)
            .updatedAt(now)
            .build();
        InterventionPlan saved = interventionPlanRepository.save(plan);
        eventPublisher.publishEvent(InterventionAssignedEvent.of(
            saved.getCampusId(), saved.getId(), saved.getStudentId(), saved.getMentorUsername(), now));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<InterventionPlanResponse> listByStudent(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        boolean privileged = user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN;
        if (privileged) {
            campusAccessService.requireAccess(user, student.getCampusId());
            return interventionPlanRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toResponse).toList();
        }
        // A mentor sees only the plans on this student that name them.
        return interventionPlanRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
            .filter(plan -> user.getRole() == UserRole.MENTOR
                && user.getUsername().equals(plan.getMentorUsername()))
            .map(this::toResponse).toList();
    }

    @Transactional
    public InterventionPlanResponse activate(String planId) {
        InterventionPlan plan = requirePlanAccess(planId);
        if (!"PROPOSED".equals(plan.getStatus())) {
            throw new InvalidInterventionTransitionException(
                "Only a PROPOSED plan can be activated; current status is " + plan.getStatus());
        }
        plan.setStatus("ACTIVE");
        plan.setUpdatedAt(OffsetDateTime.now());
        return toResponse(interventionPlanRepository.save(plan));
    }

    @Transactional
    public InterventionPlanResponse complete(String planId) {
        InterventionPlan plan = requirePlanAccess(planId);
        if (!"ACTIVE".equals(plan.getStatus())) {
            throw new InvalidInterventionTransitionException(
                "Only an ACTIVE plan can be completed; current status is " + plan.getStatus());
        }
        plan.setStatus("COMPLETED");
        plan.setUpdatedAt(OffsetDateTime.now());
        return toResponse(interventionPlanRepository.save(plan));
    }

    @Transactional
    public InterventionPlanResponse toggleAction(String planId, ToggleActionRequest request) {
        InterventionPlan plan = requirePlanAccess(planId);
        if ("COMPLETED".equals(plan.getStatus())) {
            throw new InvalidInterventionTransitionException(
                "Cannot change action items of a COMPLETED plan");
        }
        List<InterventionAction> actions = readActions(plan.getActionsJson());
        boolean found = actions.stream().anyMatch(action -> action.id().equals(request.actionId()));
        if (!found) {
            throw new IllegalArgumentException("Action not found in plan: " + request.actionId());
        }
        List<InterventionAction> updated = actions.stream()
            .map(action -> action.id().equals(request.actionId())
                ? new InterventionAction(action.id(), action.label(), request.done())
                : action)
            .toList();
        plan.setActionsJson(write(updated));
        plan.setProgress(deriveProgress(updated));
        plan.setUpdatedAt(OffsetDateTime.now());
        return toResponse(interventionPlanRepository.save(plan));
    }

    private InterventionPlan requirePlanAccess(String planId) {
        UserAccount user = campusAccessService.currentUser();
        InterventionPlan plan = interventionPlanRepository.findById(planId)
            .orElseThrow(() -> new InterventionPlanNotFoundException(planId));
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            campusAccessService.requireAccess(user, plan.getCampusId());
            return plan;
        }
        if (user.getRole() == UserRole.MENTOR && user.getUsername().equals(plan.getMentorUsername())) {
            return plan;
        }
        throw new AccessDeniedException("Only officers, admins, or the assigned mentor can change this plan");
    }

    private int deriveProgress(List<InterventionAction> actions) {
        if (actions.isEmpty()) {
            return 0;
        }
        long done = actions.stream().filter(InterventionAction::done).count();
        return (int) Math.round(100.0 * done / actions.size());
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can raise intervention plans");
        }
    }

    private InterventionPlanResponse toResponse(InterventionPlan plan) {
        return new InterventionPlanResponse(
            plan.getId(), plan.getStudentId(), plan.getMentorUsername(), plan.getRiskEntryId(),
            plan.getStatus(), plan.getCreatedAt(),
            readStrings(plan.getFocusAreasJson()), readActions(plan.getActionsJson()),
            plan.getProgress(), plan.getNotes());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize intervention plan field", exception);
        }
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read intervention plan list", exception);
        }
    }

    private List<InterventionAction> readActions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<InterventionAction>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read intervention actions", exception);
        }
    }
}
