package com.nexploy.campuslink.drive.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.CreateDriveRequest;
import com.nexploy.campuslink.drive.dto.DriveResponse;
import com.nexploy.campuslink.drive.dto.DriveRound;
import com.nexploy.campuslink.drive.dto.UpdateDriveRequest;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.exception.InvalidDriveTransitionException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.placement.entity.PlacementCycle;
import com.nexploy.campuslink.placement.repository.PlacementCycleRepository;
import com.nexploy.campuslink.recruiter.entity.Recruiter;
import com.nexploy.campuslink.recruiter.repository.RecruiterRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drive read + write. Reads build the enriched {@link DriveResponse}, parsing the drive's
 * JSON list columns (required skills, rounds, allowed branches) into typed lists. Writes
 * (create / update / changeStatus) enforce campus access + role, validate referenced
 * recruiter / cycle exist, and guard status transitions. Parsing lives here rather than in
 * the DTO, mirroring the intervention/readiness services' handling of *_json columns.
 */
@Service
@RequiredArgsConstructor
public class DriveService {

    private final ObjectMapper objectMapper;
    private final DriveRepository driveRepository;
    private final RecruiterRepository recruiterRepository;
    private final PlacementCycleRepository placementCycleRepository;
    private final CampusAccessService campusAccessService;

    /** Legal drive lifecycle transitions. Terminal states (COMPLETED, CANCELLED) allow none. */
    private static final Map<DriveStatus, Set<DriveStatus>> ALLOWED_TRANSITIONS = Map.of(
        DriveStatus.DRAFT, Set.of(DriveStatus.ACTIVE, DriveStatus.CANCELLED),
        DriveStatus.ACTIVE, Set.of(DriveStatus.CLOSED, DriveStatus.COMPLETED, DriveStatus.CANCELLED),
        DriveStatus.CLOSED, Set.of(DriveStatus.COMPLETED, DriveStatus.ACTIVE, DriveStatus.CANCELLED),
        DriveStatus.COMPLETED, Set.of(),
        DriveStatus.CANCELLED, Set.of()
    );

    // ---- Reads -----------------------------------------------------------------

    public DriveResponse toResponse(Drive drive) {
        return DriveResponse.from(
            drive,
            readStringList(drive.getRequiredSkillsJson()),
            readRounds(drive.getRoundsJson()),
            readStringList(drive.getAllowedBranchesJson())
        );
    }

    /** Parses a JSON string array, tolerating null/blank as an empty list. */
    public List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read drive list field", exception);
        }
    }

    private List<DriveRound> readRounds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DriveRound>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read drive rounds", exception);
        }
    }

    // ---- Writes ----------------------------------------------------------------

    /**
     * Creates a drive in DRAFT. Officers/admins may post for any recruiter on an accessible
     * campus; a recruiter may only post as themselves. The drive's campus is derived from the
     * recruiter (never client-supplied). Referenced recruiter and cycle must exist.
     */
    @Transactional
    public DriveResponse create(CreateDriveRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Recruiter recruiter = requireRecruiter(request.recruiterId());
        requireManageAccess(user, recruiter);
        validateCycle(request.cycleId(), recruiter.getCampusId());

        Drive drive = Drive.builder()
            .id("drv_" + UUID.randomUUID())
            .campusId(recruiter.getCampusId())
            .recruiterId(recruiter.getId())
            .cycleId(blankToNull(request.cycleId()))
            .companyName(request.companyName())
            .role(request.role())
            .location(request.location())
            .packageLpa(request.packageLpa())
            .minimumCgpa(request.minimumCgpa())
            .maximumBacklogs(request.maximumBacklogs())
            .graduationYear(request.graduationYear())
            .deadline(request.deadline())
            .status(DriveStatus.DRAFT)
            .driveType(request.driveType())
            .description(request.description())
            .expectedHiring(request.expectedHiring())
            .venue(request.venue())
            .minimumTenthPercentage(request.minimumTenthPercentage())
            .minimumTwelfthPercentage(request.minimumTwelfthPercentage())
            .requiredSkillsJson(writeJson(request.requiredSkills()))
            .roundsJson(writeJson(request.rounds()))
            .allowedBranchesJson(writeJson(request.allowedBranches()))
            .build();
        return toResponse(driveRepository.save(drive));
    }

    /**
     * Replaces a drive's editable fields. Same access rules as create; identity/audit fields
     * and status are untouched (status moves only via {@link #changeStatus}).
     */
    @Transactional
    public DriveResponse update(String driveId, UpdateDriveRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Drive drive = loadAccessible(driveId, user);
        Recruiter recruiter = requireRecruiter(request.recruiterId());
        requireManageAccess(user, recruiter);
        validateCycle(request.cycleId(), recruiter.getCampusId());

        drive.setRecruiterId(recruiter.getId());
        drive.setCampusId(recruiter.getCampusId());
        drive.setCycleId(blankToNull(request.cycleId()));
        drive.setCompanyName(request.companyName());
        drive.setRole(request.role());
        drive.setLocation(request.location());
        drive.setPackageLpa(request.packageLpa());
        drive.setMinimumCgpa(request.minimumCgpa());
        drive.setMaximumBacklogs(request.maximumBacklogs());
        drive.setGraduationYear(request.graduationYear());
        drive.setDeadline(request.deadline());
        drive.setDriveType(request.driveType());
        drive.setDescription(request.description());
        drive.setExpectedHiring(request.expectedHiring());
        drive.setVenue(request.venue());
        drive.setMinimumTenthPercentage(request.minimumTenthPercentage());
        drive.setMinimumTwelfthPercentage(request.minimumTwelfthPercentage());
        drive.setRequiredSkillsJson(writeJson(request.requiredSkills()));
        drive.setRoundsJson(writeJson(request.rounds()));
        drive.setAllowedBranchesJson(writeJson(request.allowedBranches()));
        return toResponse(driveRepository.save(drive));
    }

    /**
     * Moves a drive to {@code target} if the lifecycle allows it. Retiring transitions
     * (CLOSED/COMPLETED/CANCELLED) stamp the soft-delete audit trail. Illegal jumps → 409.
     */
    @Transactional
    public DriveResponse changeStatus(String driveId, DriveStatus target) {
        UserAccount user = campusAccessService.currentUser();
        Drive drive = loadAccessible(driveId, user);
        requireManageAccess(user, requireRecruiter(drive.getRecruiterId()));

        DriveStatus current = drive.getStatus();
        if (current == target) {
            return toResponse(drive);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidDriveTransitionException(
                "Drive " + driveId + " cannot move from " + current + " to " + target);
        }
        drive.setStatus(target);
        if (target == DriveStatus.CLOSED || target == DriveStatus.COMPLETED
            || target == DriveStatus.CANCELLED) {
            drive.setClosedAt(OffsetDateTime.now());
            drive.setClosedBy(user.getUsername());
        }
        return toResponse(driveRepository.save(drive));
    }

    // ---- Helpers ---------------------------------------------------------------

    private Drive loadAccessible(String driveId, UserAccount user) {
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());
        return drive;
    }

    private Recruiter requireRecruiter(String recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new IllegalArgumentException("Recruiter not found: " + recruiterId));
    }

    /** Optional cycle must exist and belong to the same campus. */
    private void validateCycle(String cycleId, String campusId) {
        if (cycleId == null || cycleId.isBlank()) {
            return;
        }
        PlacementCycle cycle = placementCycleRepository.findById(cycleId)
            .orElseThrow(() -> new IllegalArgumentException("Placement cycle not found: " + cycleId));
        if (!campusId.equals(cycle.getCampusId())) {
            throw new IllegalArgumentException("Placement cycle " + cycleId
                + " belongs to a different campus");
        }
    }

    /**
     * Authorizes a drive management action against the drive's recruiter/campus. Officers and
     * admins need campus access; a recruiter must own the recruiter row (username == email).
     */
    private void requireManageAccess(UserAccount user, Recruiter recruiter) {
        campusAccessService.requireAccess(user, recruiter.getCampusId());
        if (user.getRole() == UserRole.OFFICER || user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.RECRUITER) {
            Recruiter own = recruiterRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new AccessDeniedException("No recruiter profile for " + user.getUsername()));
            if (!own.getId().equals(recruiter.getId())) {
                throw new AccessDeniedException("Recruiters can only manage their own drives");
            }
            return;
        }
        throw new AccessDeniedException("Only officers, admins and recruiters can manage drives");
    }

    private String writeJson(List<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not serialize drive list field", exception);
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
