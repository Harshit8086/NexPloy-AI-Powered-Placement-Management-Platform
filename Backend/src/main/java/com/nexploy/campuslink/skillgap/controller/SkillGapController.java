package com.nexploy.campuslink.skillgap.controller;

import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse;
import com.nexploy.campuslink.skillgap.dto.SkillGapRequest;
import com.nexploy.campuslink.skillgap.dto.SkillGapResponse;
import com.nexploy.campuslink.skillgap.service.SkillGapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students/{studentId}/skill-gap")
@RequiredArgsConstructor
public class SkillGapController {

    private final SkillGapService skillGapService;

    @PostMapping
    public SkillGapResponse analyze(
        @PathVariable String studentId,
        @Valid @RequestBody SkillGapRequest request
    ) {
        return skillGapService.analyze(studentId, request);
    }

    /**
     * Skill-gap analysis for the student. If driveId is provided, returns drive-specific gap.
     * Otherwise evaluates against their campus drives.
     */
    @GetMapping
    public RoleSkillGapResponse analyzeForRole(
        @PathVariable String studentId,
        @RequestParam(required = false) String driveId
    ) {
        if (driveId != null && !driveId.isBlank()) {
            return skillGapService.analyzeForDrive(studentId, driveId.trim());
        }
        return skillGapService.analyzeForRole(studentId);
    }

    /** Skill fit for a specific drive (match info only; never gates applying). */
    @GetMapping("/drives/{driveId}")
    public RoleSkillGapResponse analyzeForDrive(
        @PathVariable String studentId,
        @PathVariable String driveId
    ) {
        return skillGapService.analyzeForDrive(studentId, driveId);
    }
}
