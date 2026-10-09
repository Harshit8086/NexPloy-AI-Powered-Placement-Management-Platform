package com.nexploy.campuslink.risk.controller;

import com.nexploy.campuslink.risk.dto.CreateInterventionRequest;
import com.nexploy.campuslink.risk.dto.InterventionPlanResponse;
import com.nexploy.campuslink.risk.dto.ToggleActionRequest;
import com.nexploy.campuslink.risk.service.InterventionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The risk -> intervention workflow. Officers/admins raise plans for at-risk students and drive the
 * PROPOSED -> ACTIVE -> COMPLETED lifecycle; the assigned mentor can also act on their own plans.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InterventionController {

    private final InterventionService interventionService;

    /** Raise a plan for a student (officer/admin only). Starts PROPOSED. */
    @PostMapping("/students/{studentId}/interventions")
    @ResponseStatus(HttpStatus.CREATED)
    public InterventionPlanResponse create(
        @PathVariable String studentId,
        @Valid @RequestBody CreateInterventionRequest request
    ) {
        return interventionService.create(studentId, request);
    }

    /** List a student's plans (officer/admin: all in campus; mentor: their own). */
    @GetMapping("/students/{studentId}/interventions")
    public List<InterventionPlanResponse> listByStudent(@PathVariable String studentId) {
        return interventionService.listByStudent(studentId);
    }

    @PostMapping("/interventions/{planId}/activate")
    public InterventionPlanResponse activate(@PathVariable String planId) {
        return interventionService.activate(planId);
    }

    @PostMapping("/interventions/{planId}/complete")
    public InterventionPlanResponse complete(@PathVariable String planId) {
        return interventionService.complete(planId);
    }

    @PostMapping("/interventions/{planId}/actions/toggle")
    public InterventionPlanResponse toggleAction(
        @PathVariable String planId,
        @Valid @RequestBody ToggleActionRequest request
    ) {
        return interventionService.toggleAction(planId, request);
    }
}
