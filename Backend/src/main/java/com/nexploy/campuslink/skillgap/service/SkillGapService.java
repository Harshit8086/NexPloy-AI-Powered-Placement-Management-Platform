package com.nexploy.campuslink.skillgap.service;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveSkillRequirement;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.repository.DriveSkillRequirementRepository;
import com.nexploy.campuslink.drive.service.DriveService;
import com.nexploy.campuslink.shared.skill.SkillNames;
import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse;
import com.nexploy.campuslink.skillgap.dto.RoleSkillGapResponse.SkillMatchItem;
import com.nexploy.campuslink.skillgap.dto.SkillGapRequest;
import com.nexploy.campuslink.skillgap.dto.SkillGapRequest.SkillInput;
import com.nexploy.campuslink.skillgap.dto.SkillGapResponse;
import com.nexploy.campuslink.skillgap.dto.SkillGapResponse.SkillGapItem;
import com.nexploy.campuslink.skillgap.narrative.SkillGapNarrator;
import com.nexploy.campuslink.skillgap.narrative.SkillGapNarrator.GapContext;
import com.nexploy.campuslink.skillgap.narrative.SkillGapNarrator.SkillNarrative;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic, explainable skill-gap analysis strictly scoped to Student + Drive pairs.
 * Evaluates student stored skills against approved Drive Skill Requirements (level-based)
 * or performs presence/coverage matching when only skill names are available.
 *
 * <p>Core Architectural Principles:
 * <ul>
 *   <li>Missing skills are marked {@code NO_EVIDENCE} / {@code UNKNOWN} — proficiency is NEVER assumed to be 0.</li>
 *   <li>{@code requiredSkillsJson} is used for presence/coverage matching only; numeric gaps are NEVER computed without target levels.</li>
 *   <li>Skill Gap is strictly Student + Drive specific (Student A + Company X ≠ Student A + Company Y).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SkillGapService {

    private static final Logger log = LoggerFactory.getLogger(SkillGapService.class);

    private final StudentRepository studentRepository;
    private final CampusAccessService campusAccessService;
    private final StudentSkillRepository studentSkillRepository;
    private final DriveRepository driveRepository;
    private final DriveSkillRequirementRepository driveSkillRequirementRepository;
    private final DriveService driveService;
    private final ApplicationRepository applicationRepository;
    private final ObjectProvider<SkillGapNarrator> narratorProvider;

    private static final int HIGH_PRIORITY_GAP = 40;
    private static final int MEDIUM_PRIORITY_GAP = 15;
    private static final int MAX_NARRATIVE_LENGTH = 600;

    /**
     * Ad-hoc what-if analysis where caller supplies required levels.
     */
    @Transactional(readOnly = true)
    public SkillGapResponse analyze(String studentId, SkillGapRequest request) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());

        Map<String, StudentSkill> storedSkills = storedSkills(studentId);

        List<SkillGapItem> gaps = request.skills().stream()
            .map(skill -> toAdHocGap(skill, storedSkills))
            .filter(gap -> gap.currentLevel() == null || gap.targetLevel() > gap.currentLevel())
            .sorted(Comparator
                .comparingInt((SkillGapItem g) -> priorityRank(g.priority()))
                .thenComparing(g -> (g.currentLevel() != null ? g.currentLevel() : 0) - g.targetLevel()))
            .toList();

        gaps = withNarrative(request.targetRole(), gaps);
        return new SkillGapResponse(studentId, request.targetRole(), gaps);
    }

    /**
     * Evaluates skill gap for a specific drive against its approved requirements
     * or presence-based skill list fallback.
     */
    @Transactional(readOnly = true)
    public RoleSkillGapResponse analyzeForDrive(String studentId, String driveId) {
        Student student = requireStudentAccess(studentId);
        UserAccount user = campusAccessService.currentUser();
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());

        String role = drive.getRole();
        boolean hasRole = role != null && !role.isBlank();
        Map<String, StudentSkill> storedSkills = storedSkills(studentId);

        // 1. Check for approved per-drive skill requirements (Level-based engine)
        List<DriveSkillRequirement> requirements =
            driveSkillRequirementRepository.findByDriveIdAndApprovedTrueOrderByWeightDescSkillAsc(driveId);

        if (!requirements.isEmpty()) {
            return buildLevelBasedDriveAnalysis(studentId, drive, requirements, storedSkills);
        }

        // 2. Fall back to presence-only matching against Drive.requiredSkillsJson (NO fake numeric gaps)
        List<String> driveSkills = driveService.readStringList(drive.getRequiredSkillsJson());
        if (!driveSkills.isEmpty()) {
            return buildPresenceOnlyDriveAnalysis(studentId, drive, driveSkills, storedSkills);
        }

        // 3. No requirements configured for this drive yet
        return new RoleSkillGapResponse(
            studentId, hasRole ? role : "", hasRole, !storedSkills.isEmpty(),
            false, false, 0, List.of(), List.of(),
            drive.getId(), drive.getCompanyName(), 0, 0, "EMPTY"
        );
    }

    /**
     * Resolves the primary active drive on campus for the student (or matching their target role)
     * to provide an initial drive-scoped gap analysis.
     */
    @Transactional(readOnly = true)
    public RoleSkillGapResponse analyzeForRole(String studentId) {
        Student student = requireStudentAccess(studentId);
        List<Drive> campusDrives = driveRepository.findByCampusId(student.getCampusId());

        if (campusDrives.isEmpty()) {
            boolean hasTargetRole = student.getTargetRole() != null && !student.getTargetRole().isBlank();
            return new RoleSkillGapResponse(
                studentId, hasTargetRole ? student.getTargetRole() : "", hasTargetRole,
                !storedSkills(studentId).isEmpty(), false, false, 0, List.of(), List.of(),
                null, null, 0, 0, "NO_ACTIVE_DRIVES"
            );
        }

        Drive selectedDrive = null;

        // 1. HIGHEST AUTOMATIC PRIORITY (Option 3): Check student's active applied drives
        List<Application> applications = applicationRepository.findByStudentId(studentId);
        if (!applications.isEmpty()) {
            Map<String, Drive> activeCampusDriveMap = campusDrives.stream()
                .filter(d -> d.getStatus() == DriveStatus.ACTIVE)
                .collect(Collectors.toMap(Drive::getId, d -> d, (a, b) -> a));

            for (Application app : applications) {
                Drive applied = activeCampusDriveMap.get(app.getDriveId());
                if (applied != null) {
                    selectedDrive = applied;
                    break;
                }
            }
        }

        // 2. FALLBACK MATCHING (Option 2): Match active campus drives with student's targetRole
        if (selectedDrive == null && student.getTargetRole() != null && !student.getTargetRole().isBlank()) {
            String target = student.getTargetRole().trim();
            for (Drive d : campusDrives) {
                if (d.getStatus() == DriveStatus.ACTIVE && d.getRole() != null) {
                    if (d.getRole().equalsIgnoreCase(target) || d.getRole().toLowerCase().contains(target.toLowerCase())) {
                        selectedDrive = d;
                        break;
                    }
                }
            }
        }

        // 3. ULTIMATE FALLBACK: Pick the first active drive, or first available drive
        if (selectedDrive == null) {
            selectedDrive = campusDrives.stream()
                .filter(d -> d.getStatus() == DriveStatus.ACTIVE)
                .findFirst()
                .orElse(campusDrives.get(0));
        }

        return analyzeForDrive(studentId, selectedDrive.getId());
    }

    /**
     * Engine 1: Level-based analysis using approved DriveSkillRequirement rows.
     * Missing skills are marked NO_EVIDENCE / UNKNOWN (never currentLevel = 0).
     */
    private RoleSkillGapResponse buildLevelBasedDriveAnalysis(
        String studentId, Drive drive,
        List<DriveSkillRequirement> requirements,
        Map<String, StudentSkill> storedSkills
    ) {
        List<SkillMatchItem> strengths = new ArrayList<>();
        List<SkillGapItem> gaps = new ArrayList<>();
        double weightedScore = 0;
        double totalWeight = 0;
        int coveredCount = 0;

        for (DriveSkillRequirement req : requirements) {
            String skill = req.getSkill();
            int requiredLevel = req.getRequiredLevel() != null ? req.getRequiredLevel() : 50;
            int weight = req.getWeight() != null && req.getWeight() > 0 ? req.getWeight() : 1;
            boolean mandatory = req.getMandatory() != null ? req.getMandatory() : true;
            totalWeight += weight;

            StudentSkill stored = storedSkills.get(SkillNames.normalize(skill));

            if (stored != null && stored.getProficiency() != null) {
                // Student has recorded evidence / proficiency
                int currentLevel = stored.getProficiency();
                String verificationStatus = stored.getVerificationStatus();
                String evidenceStatus = verificationStatus != null ? verificationStatus : "SELF_DECLARED";
                coveredCount++;

                double skillFit = requiredLevel <= 0 ? 1.0 : Math.min((double) currentLevel / requiredLevel, 1.0);
                weightedScore += skillFit * weight;

                if (currentLevel >= requiredLevel) {
                    strengths.add(new SkillMatchItem(skill, currentLevel, requiredLevel, verificationStatus, evidenceStatus));
                } else {
                    int gap = requiredLevel - currentLevel;
                    String priority = priorityFor(gap);
                    gaps.add(new SkillGapItem(
                        skill,
                        currentLevel,
                        requiredLevel,
                        label(currentLevel),
                        label(requiredLevel),
                        priority,
                        whyItMatters(skill, priority, drive.getCompanyName()),
                        recommendedAction(skill, gap),
                        verificationStatus,
                        evidenceStatus
                    ));
                }
            } else {
                // MISSING SKILL: Student has NO EVIDENCE. Do NOT set currentLevel = 0!
                // Requirement is unmet (score contribution 0.0), but actual ability is unknown.
                String priority = mandatory ? "HIGH" : "MEDIUM";
                gaps.add(new SkillGapItem(
                    skill,
                    null, // currentLevel is null (UNKNOWN / NO_EVIDENCE)
                    requiredLevel,
                    "No Evidence",
                    label(requiredLevel),
                    priority,
                    whyItMattersNoEvidence(skill, drive.getCompanyName(), mandatory),
                    recommendedActionNoEvidence(skill),
                    null,
                    "NO_EVIDENCE"
                ));
            }
        }

        int overallMatch = totalWeight <= 0 ? 0 : (int) Math.round((weightedScore / totalWeight) * 100);

        gaps = new ArrayList<>(gaps);
        gaps.sort(Comparator
            .comparingInt((SkillGapItem g) -> priorityRank(g.priority()))
            .thenComparing(g -> (g.currentLevel() != null ? g.currentLevel() : 0) - (g.targetLevel() != null ? g.targetLevel() : 0)));

        String roleContext = (drive.getRole() != null ? drive.getRole() : "") + " at " + drive.getCompanyName();
        gaps = withNarrative(roleContext, gaps);

        return new RoleSkillGapResponse(
            studentId,
            drive.getRole() != null ? drive.getRole() : "",
            drive.getRole() != null && !drive.getRole().isBlank(),
            !storedSkills.isEmpty(),
            true,  // roleRequirementsAvailable = true
            false, // driveSkillsFallback = false
            overallMatch,
            strengths,
            gaps,
            drive.getId(),
            drive.getCompanyName(),
            coveredCount,
            requirements.size(),
            "LEVEL_BASED"
        );
    }

    /**
     * Engine 2: Presence-Only Matching using Drive.requiredSkillsJson.
     * Never calculates numerical gap (gap = requiredLevel - currentLevel) because required levels do not exist.
     */
    private RoleSkillGapResponse buildPresenceOnlyDriveAnalysis(
        String studentId, Drive drive,
        List<String> driveSkills,
        Map<String, StudentSkill> storedSkills
    ) {
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (String skill : driveSkills) {
            if (skill != null && !skill.isBlank()) {
                displayNames.putIfAbsent(SkillNames.normalize(skill), skill.trim());
            }
        }

        if (displayNames.isEmpty()) {
            return new RoleSkillGapResponse(
                studentId, drive.getRole() != null ? drive.getRole() : "",
                drive.getRole() != null && !drive.getRole().isBlank(),
                !storedSkills.isEmpty(), false, false, 0, List.of(), List.of(),
                drive.getId(), drive.getCompanyName(), 0, 0, "PRESENCE_ONLY"
            );
        }

        List<SkillMatchItem> strengths = new ArrayList<>();
        List<SkillGapItem> gaps = new ArrayList<>();

        for (String displayName : displayNames.values()) {
            StudentSkill stored = storedSkills.get(SkillNames.normalize(displayName));
            if (stored != null) {
                int level = stored.getProficiency() != null ? stored.getProficiency() : 50;
                strengths.add(new SkillMatchItem(
                    displayName, level, level, stored.getVerificationStatus(), "COVERED"
                ));
            } else {
                // Presence missing: NO numeric gap calculation
                gaps.add(new SkillGapItem(
                    displayName,
                    null,
                    null,
                    "No Evidence",
                    "Required",
                    "MEDIUM",
                    displayName + " is requested by " + drive.getCompanyName() + ". No evidence found on your profile.",
                    "Add " + displayName + " to your skills or link a project to demonstrate coverage.",
                    null,
                    "NO_EVIDENCE"
                ));
            }
        }

        strengths.sort(Comparator.comparingInt(SkillMatchItem::currentLevel).reversed());
        int overallMatch = (int) Math.round(100.0 * strengths.size() / displayNames.size());

        return new RoleSkillGapResponse(
            studentId,
            drive.getRole() != null ? drive.getRole() : "",
            drive.getRole() != null && !drive.getRole().isBlank(),
            !storedSkills.isEmpty(),
            false, // roleRequirementsAvailable = false (no levels)
            true,  // driveSkillsFallback = true
            overallMatch,
            strengths,
            gaps,
            drive.getId(),
            drive.getCompanyName(),
            strengths.size(),
            displayNames.size(),
            "PRESENCE_ONLY"
        );
    }

    private SkillGapItem toAdHocGap(SkillInput input, Map<String, StudentSkill> storedSkills) {
        StudentSkill stored = storedSkills.get(SkillNames.normalize(input.skill()));
        Integer current = input.currentLevel() != null
            ? input.currentLevel()
            : (stored != null ? stored.getProficiency() : null);

        int target = input.requiredLevel() != null ? input.requiredLevel() : 50;
        String verificationStatus = stored != null ? stored.getVerificationStatus() : null;

        if (current == null) {
            return new SkillGapItem(
                input.skill(),
                null,
                target,
                "No Evidence",
                label(target),
                "HIGH",
                input.skill() + " is specified as a requirement, but no evidence exists on your profile.",
                "Take an assessment or add project work for " + input.skill() + ".",
                verificationStatus,
                "NO_EVIDENCE"
            );
        }

        int gap = target - current;
        String priority = priorityFor(gap);
        return new SkillGapItem(
            input.skill(),
            current,
            target,
            label(current),
            label(target),
            priority,
            whyItMatters(input.skill(), priority, null),
            recommendedAction(input.skill(), gap),
            verificationStatus,
            verificationStatus != null ? verificationStatus : "SELF_DECLARED"
        );
    }

    private Student requireStudentAccess(String studentId) {
        UserAccount user = campusAccessService.currentUser();
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));
        campusAccessService.requireAccess(user, student.getCampusId());
        return student;
    }

    private Map<String, StudentSkill> storedSkills(String studentId) {
        return studentSkillRepository.findByStudentIdOrderBySkillAsc(studentId).stream()
            .collect(Collectors.toMap(
                s -> SkillNames.normalize(s.getSkill()),
                s -> s,
                (a, b) -> a));
    }

    private List<SkillGapItem> withNarrative(String contextRole, List<SkillGapItem> gaps) {
        SkillGapNarrator narrator = narratorProvider.getIfAvailable();
        if (narrator == null || gaps.isEmpty()) {
            return gaps;
        }
        Map<String, SkillNarrative> narratives;
        try {
            List<GapContext> context = gaps.stream()
                .map(g -> new GapContext(
                    g.skill(),
                    g.currentLevel() != null ? g.currentLevel() : 0,
                    g.targetLevel() != null ? g.targetLevel() : 0,
                    g.targetLevel() != null ? (g.targetLevel() - (g.currentLevel() != null ? g.currentLevel() : 0)) : 0,
                    g.priority(),
                    g.currentLabel(),
                    g.targetLabel() != null ? g.targetLabel() : ""))
                .toList();
            narratives = narrator.narrate(contextRole != null ? contextRole : "Role", context);
        } catch (RuntimeException e) {
            log.warn("Skill-gap narrator failed; using deterministic text. cause={}", e.toString());
            return gaps;
        }
        if (narratives == null || narratives.isEmpty()) {
            return gaps;
        }
        return gaps.stream()
            .map(g -> {
                SkillNarrative n = narratives.get(g.skill());
                if (n == null) {
                    return g;
                }
                String why = valid(n.whyItMatters()) ? n.whyItMatters().trim() : g.whyItMatters();
                String action = valid(n.recommendedAction()) ? n.recommendedAction().trim() : g.recommendedAction();
                return new SkillGapItem(
                    g.skill(),
                    g.currentLevel(),
                    g.targetLevel(),
                    g.currentLabel(),
                    g.targetLabel(),
                    g.priority(),
                    why,
                    action,
                    g.verificationStatus(),
                    g.evidenceStatus()
                );
            })
            .toList();
    }

    private boolean valid(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_NARRATIVE_LENGTH;
    }

    private String priorityFor(int gap) {
        if (gap >= HIGH_PRIORITY_GAP) {
            return "HIGH";
        }
        if (gap >= MEDIUM_PRIORITY_GAP) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private String label(int level) {
        if (level == 0) return "None";
        if (level < 30) return "Beginner";
        if (level < 60) return "Intermediate";
        if (level < 85) return "Advanced";
        return "Expert";
    }

    private String whyItMatters(String skill, String priority, String companyName) {
        String companyPrefix = companyName != null ? companyName + " emphasizes " + skill + "; " : "";
        return switch (priority) {
            case "HIGH" -> companyPrefix + skill + " is a core requirement; closing this gap is critical for shortlisting.";
            case "MEDIUM" -> companyPrefix + skill + " is an expected skill; strengthening it meaningfully improves your match score.";
            default -> companyPrefix + skill + " is close to the expected target; minor practice will secure it.";
        };
    }

    private String whyItMattersNoEvidence(String skill, String companyName, boolean mandatory) {
        String prefix = companyName != null ? companyName : "This drive";
        return prefix + " lists " + skill + " as " + (mandatory ? "a mandatory" : "an expected")
            + " requirement. Since no evidence is recorded on your profile, proving proficiency is key.";
    }

    private String recommendedAction(String skill, int gap) {
        if (gap >= HIGH_PRIORITY_GAP) {
            return "Prioritise " + skill + ": complete targeted assessments and feature it in an active project.";
        }
        if (gap >= MEDIUM_PRIORITY_GAP) {
            return "Practise " + skill + " with focused problems and add it to your project portfolio.";
        }
        return "Review " + skill + " concepts to close the remaining gap.";
    }

    private String recommendedActionNoEvidence(String skill) {
        return "Take an assessment or submit project evidence for " + skill + " to demonstrate your capability.";
    }
}
