package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.drive.dto.DriveResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.entity.DriveStatus;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.DriveService;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.student.dto.CareerRecommendationResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import com.nexploy.campuslink.shared.skill.SkillNames;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Deterministic, explainable career recommendations: ranks the student's own campus's OPEN drives
 * by how well they fit the student's recorded skills, eligibility and role preferences. Every input
 * is real stored data — the student's {@link StudentSkill} rows and strengths, and each drive's
 * stored criteria — so an empty campus (no open drives) yields an empty list rather than fabricated
 * opportunities. Identical inputs always produce identical output; ties break on package then id.
 */
@Service
@RequiredArgsConstructor
public class CareerRecommendationService {

    /** Weight ceilings for the three signals; they sum to 100 so matchScore is a clean percentage. */
    private static final int SKILL_WEIGHT = 60;
    private static final int ELIGIBILITY_WEIGHT = 25;
    private static final int ROLE_WEIGHT = 15;

    /** Neutral skill credit when a drive lists no required skills — neither rewarded nor penalised. */
    private static final int NEUTRAL_SKILL_SCORE = 30;

    /**
     * How much a matched skill counts toward the skill score, by its verification level. A VERIFIED
     * skill (passed an AI viva) counts full; EVIDENCED (proof link) counts most; a bare SELF_DECLARED
     * skill (or a resume strength) counts least — so a self-declared skill set can't inflate the match
     * the way a verified one does. Tunable.
     */
    private static final double WEIGHT_VERIFIED = 1.0;
    private static final double WEIGHT_EVIDENCED = 0.7;
    private static final double WEIGHT_SELF_DECLARED = 0.5;

    /** Most recommendations returned; the best-fitting open drives, not an exhaustive dump. */
    private static final int MAX_RECOMMENDATIONS = 6;

    private final DriveRepository driveRepository;
    private final DriveService driveService;
    private final StudentSkillRepository studentSkillRepository;
    private final EligibilityService eligibilityService;

    public List<CareerRecommendationResponse> recommend(Student student) {
        Map<String, Double> presentSkills = presentSkillWeights(student);
        List<String> roleTargets = roleTargets(student);

        return driveRepository.findByCampusId(student.getCampusId()).stream()
            .filter(drive -> drive.getStatus() == DriveStatus.ACTIVE)
            .map(drive -> score(student, drive, presentSkills, roleTargets))
            .sorted(Comparator
                .comparingInt(CareerRecommendationResponse::matchScore).reversed()
                .thenComparing(r -> r.packageLpa() == null ? BigDecimal.ZERO : r.packageLpa(),
                    Comparator.reverseOrder())
                .thenComparing(CareerRecommendationResponse::driveId))
            .limit(MAX_RECOMMENDATIONS)
            .toList();
    }

    /** Builds the scored, explainable recommendation for a single drive. */
    private CareerRecommendationResponse score(
        Student student, Drive drive, Map<String, Double> presentSkills, List<String> roleTargets) {
        DriveResponse view = driveService.toResponse(drive);
        List<String> requiredSkills = view.requiredSkills();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        double matchedWeight = 0.0;
        for (String skill : requiredSkills) {
            Double factor = presentSkills.get(SkillNames.normalize(skill));
            if (factor != null) {
                matched.add(skill);
                matchedWeight += factor;
            } else {
                missing.add(skill);
            }
        }

        // Verification-weighted coverage: a matched skill contributes its verification factor (not a
        // flat 1), so verified skills lift the score and self-declared ones lift it less.
        int skillScore = requiredSkills.isEmpty()
            ? NEUTRAL_SKILL_SCORE
            : (int) Math.round(SKILL_WEIGHT * matchedWeight / requiredSkills.size());

        boolean eligible = false;
        int eligibilityScore = 0;
        boolean eligibilityKnown = false;
        int passed = 0;
        int total = 0;
        try {
            var eligibility = eligibilityService.evaluate(drive.getId(), student.getId());
            eligible = eligibility.eligible();
            total = eligibility.checks().size();
            passed = (int) eligibility.checks().stream().filter(c -> c.passed()).count();
            eligibilityScore = total == 0
                ? ELIGIBILITY_WEIGHT
                : (int) Math.round((double) ELIGIBILITY_WEIGHT * passed / total);
            eligibilityKnown = true;
        } catch (RuntimeException exception) {
            // Missing/incomplete profile inputs (e.g. no CGPA on record) — treat eligibility as
            // undetermined rather than fabricating a pass. Score contribution stays 0.
            eligibilityScore = 0;
        }

        String driveRole = drive.getRole() == null ? "" : drive.getRole().toLowerCase();
        int roleScore = roleScore(driveRole, roleTargets);

        int matchScore = Math.max(0, Math.min(100, skillScore + eligibilityScore + roleScore));

        List<String> reasons = reasons(matched, matchedWeight, requiredSkills, eligible, eligibilityKnown,
            passed, total, roleScore, drive.getRole());

        return new CareerRecommendationResponse(
            drive.getId(), drive.getCompanyName(), drive.getRole(), drive.getLocation(),
            drive.getPackageLpa(), matchScore, eligible, List.copyOf(matched), List.copyOf(missing), reasons);
    }

    /** 15 for an exact target-role match, 8 for a partial token overlap, else 0. Deterministic. */
    private int roleScore(String driveRole, List<String> roleTargets) {
        if (driveRole.isBlank() || roleTargets.isEmpty()) {
            return 0;
        }
        if (roleTargets.contains(driveRole)) {
            return ROLE_WEIGHT;
        }
        Set<String> driveTokens = Set.of(driveRole.split("\\s+"));
        boolean overlaps = roleTargets.stream()
            .flatMap(target -> Set.of(target.split("\\s+")).stream())
            .anyMatch(driveTokens::contains);
        return overlaps ? ROLE_WEIGHT / 2 : 0;
    }

    /** Human-readable, non-fabricated explanation of why the drive was ranked where it was. */
    private List<String> reasons(
        List<String> matched, double matchedWeight, List<String> required, boolean eligible,
        boolean eligibilityKnown, int passed, int total, int roleScore, String role) {
        List<String> reasons = new ArrayList<>();
        if (!required.isEmpty()) {
            reasons.add("Matches " + matched.size() + " of " + required.size()
                + " required skills" + (matched.isEmpty() ? "" : ": " + String.join(", ", matched)));
        }
        // Nudge: at least one matched skill is not fully VERIFIED, so verifying lifts this score.
        if (!matched.isEmpty() && matchedWeight < matched.size() - 0.001) {
            reasons.add("Verify your matched skills (skill viva) to rank higher — self-declared skills count less");
        }
        if (!eligibilityKnown) {
            reasons.add("Eligibility could not be determined — complete your profile to check");
        } else if (eligible) {
            reasons.add("You meet every eligibility criterion");
        } else {
            reasons.add("Eligible on " + passed + " of " + total + " criteria");
        }
        if (roleScore == ROLE_WEIGHT) {
            reasons.add("Aligned with your preferred role: " + role);
        } else if (roleScore > 0) {
            reasons.add("Related to your preferred roles");
        }
        return reasons;
    }

    /**
     * The student's present skills (keyed by {@link SkillNames#normalize}) mapped to a verification
     * weight: recorded skills at their verification factor, unioned with resume strengths (counted
     * as self-declared). When a skill appears in both, the stronger (max) factor wins.
     */
    private Map<String, Double> presentSkillWeights(Student student) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (StudentSkill skill : studentSkillRepository.findByStudentIdOrderBySkillAsc(student.getId())) {
            if (skill.getSkill() != null && !skill.getSkill().isBlank()) {
                weights.merge(SkillNames.normalize(skill.getSkill()), factorFor(skill.getVerificationStatus()), Math::max);
            }
        }
        for (String strength : driveService.readStringList(student.getStrengthsJson())) {
            if (strength != null && !strength.isBlank()) {
                weights.merge(SkillNames.normalize(strength), WEIGHT_SELF_DECLARED, Math::max);
            }
        }
        return weights;
    }

    /** Verification level -> skill weight. Unknown/null (older rows) is treated as self-declared. */
    private static double factorFor(String verificationStatus) {
        if ("VERIFIED".equals(verificationStatus)) {
            return WEIGHT_VERIFIED;
        }
        if ("EVIDENCED".equals(verificationStatus)) {
            return WEIGHT_EVIDENCED;
        }
        return WEIGHT_SELF_DECLARED;
    }

    /** The student's role preferences (lowercased): target role unioned with preferred roles. */
    private List<String> roleTargets(Student student) {
        Set<String> targets = new LinkedHashSet<>();
        if (student.getTargetRole() != null && !student.getTargetRole().isBlank()) {
            targets.add(student.getTargetRole().toLowerCase());
        }
        for (String role : driveService.readStringList(student.getPreferredRolesJson())) {
            if (role != null && !role.isBlank()) {
                targets.add(role.toLowerCase());
            }
        }
        return List.copyOf(targets);
    }
}
