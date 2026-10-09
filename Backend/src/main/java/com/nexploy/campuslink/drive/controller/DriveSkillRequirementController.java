package com.nexploy.campuslink.drive.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.BulkUpsertDriveRequirementsRequest;
import com.nexploy.campuslink.drive.dto.DriveSkillRequirementResponse;
import com.nexploy.campuslink.drive.dto.ExtractDriveSkillsResponse;
import com.nexploy.campuslink.drive.dto.UpsertDriveSkillRequirementRequest;
import com.nexploy.campuslink.drive.service.DriveSkillRequirementService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing per-drive skill requirement management, AI extraction trigger,
 * and approval endpoints for Placement Officers, Recruiters, and Students.
 */
@RestController
@RequestMapping("/api/drives/{driveId}/skill-requirements")
@RequiredArgsConstructor
public class DriveSkillRequirementController {

    private final DriveSkillRequirementService service;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<DriveSkillRequirementResponse> list(
        @PathVariable String driveId,
        @RequestParam(defaultValue = "false") boolean approvedOnly
    ) {
        UserAccount user = campusAccessService.currentUser();
        // Students can only see approved requirements
        boolean filterApproved = approvedOnly || (user.getRole() == UserRole.STUDENT);
        return service.listRequirements(driveId, filterApproved);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DriveSkillRequirementResponse create(
        @PathVariable String driveId,
        @Valid @RequestBody UpsertDriveSkillRequirementRequest request
    ) {
        requireManager();
        return service.upsertRequirement(driveId, request);
    }

    @PutMapping("/{id}")
    public DriveSkillRequirementResponse update(
        @PathVariable String driveId,
        @PathVariable String id,
        @Valid @RequestBody UpsertDriveSkillRequirementRequest request
    ) {
        requireManager();
        return service.upsertRequirement(driveId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable String driveId,
        @PathVariable String id
    ) {
        requireManager();
        service.deleteRequirement(driveId, id);
    }

    @PostMapping("/bulk")
    public List<DriveSkillRequirementResponse> bulkUpsert(
        @PathVariable String driveId,
        @Valid @RequestBody BulkUpsertDriveRequirementsRequest request
    ) {
        requireManager();
        return service.bulkUpsert(driveId, request);
    }

    @PostMapping("/{id}/approve")
    public DriveSkillRequirementResponse approve(
        @PathVariable String driveId,
        @PathVariable String id
    ) {
        requireManager();
        return service.approveRequirement(driveId, id);
    }

    @PostMapping("/approve-all")
    public int approveAll(@PathVariable String driveId) {
        requireManager();
        return service.approveAll(driveId);
    }

    @PostMapping("/extract-ai")
    public ExtractDriveSkillsResponse extractAi(@PathVariable String driveId) {
        requireManager();
        return service.extractSkillsWithAi(driveId);
    }

    private UserAccount requireManager() {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.RECRUITER) {
            throw new AccessDeniedException("Only officers, recruiters, and admins can manage drive skill requirements");
        }
        return user;
    }
}
