package com.nexploy.campuslink.schedule.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.dto.OptimizationResponse;
import com.nexploy.campuslink.schedule.dto.RejectRunRequest;
import com.nexploy.campuslink.schedule.entity.ScheduleConflictRecord;
import com.nexploy.campuslink.schedule.exception.ScheduleConflictNotFoundException;
import com.nexploy.campuslink.schedule.repository.ScheduleConflictRepository;
import com.nexploy.campuslink.schedule.service.ScheduleOptimizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class OptimizationController {

    private final ScheduleOptimizationService scheduleOptimizationService;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final CampusAccessService campusAccessService;

    @PostMapping("/optimize")
    public OptimizationResponse optimize(@RequestParam(required = false) String campusId) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can run schedule optimization");
        }
        String scopedCampusId = campusId;
        if (scopedCampusId == null || scopedCampusId.isBlank()) {
            scopedCampusId = user.getCampusId();
        }
        campusAccessService.requireAccess(user, scopedCampusId);
        return scheduleOptimizationService.optimize(scopedCampusId);
    }

    /**
     * Optimizes the campus that owns a specific conflict (OR_TOOLS §6). The conflict resolves to a
     * campus, access is enforced, and the same campus-scoped optimization runs.
     */
    @PostMapping("/conflicts/{conflictId}/optimize")
    public OptimizationResponse optimizeConflict(@PathVariable String conflictId) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can run schedule optimization");
        }
        ScheduleConflictRecord conflict = scheduleConflictRepository.findById(conflictId)
            .orElseThrow(() -> new ScheduleConflictNotFoundException(conflictId));
        campusAccessService.requireAccess(user, conflict.getCampusId());
        return scheduleOptimizationService.optimize(conflict.getCampusId());
    }

    /** Reads a persisted optimization run (OR_TOOLS §6). */
    @GetMapping("/optimization-runs/{runId}")
    public OptimizationResponse getRun(@PathVariable String runId) {
        campusAccessService.currentUser();
        return scheduleOptimizationService.get(runId);
    }

    @PostMapping("/optimization-runs/{runId}/approve")
    public OptimizationResponse approve(@PathVariable String runId) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can approve schedules");
        }
        return scheduleOptimizationService.approve(runId);
    }

    /** Rejects a pending proposal with a recorded reason, leaving the active schedule unchanged. */
    @PostMapping("/optimization-runs/{runId}/reject")
    public OptimizationResponse reject(@PathVariable String runId, @Valid @RequestBody RejectRunRequest request) {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can reject schedules");
        }
        return scheduleOptimizationService.reject(runId, request.reason());
    }
}
