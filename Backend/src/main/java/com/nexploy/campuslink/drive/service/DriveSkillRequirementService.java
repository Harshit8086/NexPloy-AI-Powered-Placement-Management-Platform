package com.nexploy.campuslink.drive.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.BulkUpsertDriveRequirementsRequest;
import com.nexploy.campuslink.drive.dto.DriveSkillRequirementResponse;
import com.nexploy.campuslink.drive.dto.ExtractDriveSkillsResponse;
import com.nexploy.campuslink.drive.dto.ExtractedSkillItem;
import com.nexploy.campuslink.drive.dto.UpsertDriveSkillRequirementRequest;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing per-drive skill requirements.
 * Coordinates manual CRUD by Placement Officers, AI-assisted extraction from JDs via Spring AI,
 * and approval workflows before requirements participate in student skill-gap analysis.
 */
@Service
@RequiredArgsConstructor
public class DriveSkillRequirementService {

    private final DriveRepository driveRepository;
    private final DriveSkillRequirementRepository requirementRepository;
    private final DriveService driveService;
    private final DriveSkillExtractionService extractionService;
    private final CampusAccessService campusAccessService;

    @Transactional(readOnly = true)
    public List<DriveSkillRequirementResponse> listRequirements(String driveId, boolean approvedOnly) {
        Drive drive = loadAccessibleDrive(driveId);
        List<DriveSkillRequirement> list = approvedOnly
            ? requirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc(drive.getId())
            : requirementRepository.findByDriveIdOrderByWeightDescSkillAsc(drive.getId());
        return list.stream().map(DriveSkillRequirementResponse::from).toList();
    }

    @Transactional
    public DriveSkillRequirementResponse upsertRequirement(String driveId, UpsertDriveSkillRequirementRequest request) {
        Drive drive = loadAccessibleDrive(driveId);
        DriveSkillRequirement req = requirementRepository.findByDriveIdAndSkillIgnoreCase(drive.getId(), request.skill().trim())
            .orElseGet(() -> DriveSkillRequirement.builder()
                .id("dsr_" + UUID.randomUUID())
                .driveId(drive.getId())
                .createdAt(OffsetDateTime.now())
                .build());

        req.setSkill(request.skill().trim());
        req.setRequiredLevel(request.requiredLevel());
        req.setWeight(request.weight() != null ? request.weight() : 1);
        req.setMandatory(request.mandatory() != null ? request.mandatory() : true);
        req.setSource(request.source() != null ? request.source() : "OFFICER");
        req.setConfidence(request.confidence());
        req.setApproved(request.approved() != null ? request.approved() : true);
        req.setUpdatedAt(OffsetDateTime.now());

        return DriveSkillRequirementResponse.from(requirementRepository.save(req));
    }

    @Transactional
    public List<DriveSkillRequirementResponse> bulkUpsert(String driveId, BulkUpsertDriveRequirementsRequest request) {
        Drive drive = loadAccessibleDrive(driveId);
        List<DriveSkillRequirementResponse> responses = new ArrayList<>();
        if (request.requirements() != null) {
            for (UpsertDriveSkillRequirementRequest item : request.requirements()) {
                responses.add(upsertRequirement(drive.getId(), item));
            }
        }
        return responses;
    }

    @Transactional
    public void deleteRequirement(String driveId, String requirementId) {
        Drive drive = loadAccessibleDrive(driveId);
        DriveSkillRequirement req = requirementRepository.findById(requirementId)
            .orElseThrow(() -> new IllegalArgumentException("Drive skill requirement not found: " + requirementId));
        if (!req.getDriveId().equals(drive.getId())) {
            throw new IllegalArgumentException("Requirement does not belong to drive " + driveId);
        }
        requirementRepository.delete(req);
    }

    @Transactional
    public DriveSkillRequirementResponse approveRequirement(String driveId, String requirementId) {
        Drive drive = loadAccessibleDrive(driveId);
        DriveSkillRequirement req = requirementRepository.findById(requirementId)
            .orElseThrow(() -> new IllegalArgumentException("Drive skill requirement not found: " + requirementId));
        if (!req.getDriveId().equals(drive.getId())) {
            throw new IllegalArgumentException("Requirement does not belong to drive " + driveId);
        }
        req.setApproved(true);
        req.setUpdatedAt(OffsetDateTime.now());
        return DriveSkillRequirementResponse.from(requirementRepository.save(req));
    }

    @Transactional
    public int approveAll(String driveId) {
        Drive drive = loadAccessibleDrive(driveId);
        List<DriveSkillRequirement> list = requirementRepository.findByDriveId(drive.getId());
        for (DriveSkillRequirement r : list) {
            r.setApproved(true);
            r.setUpdatedAt(OffsetDateTime.now());
        }
        requirementRepository.saveAll(list);
        return list.size();
    }

    @Transactional
    public ExtractDriveSkillsResponse extractSkillsWithAi(String driveId) {
        Drive drive = loadAccessibleDrive(driveId);
        List<String> existingSkills = driveService.toResponse(drive).requiredSkills();
        List<ExtractedSkillItem> extracted = extractionService.extractSkills(
            drive.getId(),
            drive.getRole(),
            drive.getDescription(),
            existingSkills
        );

        // Save newly extracted skills as draft requirements (approved = false, source = AI_EXTRACTED)
        // for placement officer review
        for (ExtractedSkillItem item : extracted) {
            if (requirementRepository.findByDriveIdAndSkillIgnoreCase(drive.getId(), item.skill().trim()).isEmpty()) {
                DriveSkillRequirement req = DriveSkillRequirement.builder()
                    .id("dsr_" + UUID.randomUUID())
                    .driveId(drive.getId())
                    .skill(item.skill().trim())
                    .requiredLevel(item.requiredLevel())
                    .weight(item.weight())
                    .mandatory(item.mandatory())
                    .source("AI_EXTRACTED")
                    .confidence(item.confidence())
                    .approved(false) // requires officer review & approval
                    .createdAt(OffsetDateTime.now())
                    .updatedAt(OffsetDateTime.now())
                    .build();
                requirementRepository.save(req);
            }
        }

        return new ExtractDriveSkillsResponse(drive.getId(), drive.getRole(), extracted, "AI_EXTRACTED");
    }

    private Drive loadAccessibleDrive(String driveId) {
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, drive.getCampusId());
        return drive;
    }
}
