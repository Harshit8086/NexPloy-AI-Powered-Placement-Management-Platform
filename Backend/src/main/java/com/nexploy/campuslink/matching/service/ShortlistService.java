package com.nexploy.campuslink.matching.service;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.exception.NotEligibleException;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.matching.dto.AutoShortlistRequest;
import com.nexploy.campuslink.matching.dto.AutoShortlistResponse;
import com.nexploy.campuslink.matching.dto.ShortlistDecisionResponse;
import com.nexploy.campuslink.matching.dto.ShortlistRequest;
import com.nexploy.campuslink.matching.entity.MatchResult;
import com.nexploy.campuslink.matching.entity.ShortlistDecision;
import com.nexploy.campuslink.matching.event.ShortlistDecidedEvent;
import com.nexploy.campuslink.matching.repository.MatchResultRepository;
import com.nexploy.campuslink.matching.repository.ShortlistDecisionRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Officer-facing shortlisting over scored matches. Records an immutable
 * {@link ShortlistDecision} and advances the application funnel accordingly
 * (SHORTLIST -> SHORTLISTED, REJECT -> REJECTED), linking the latest persisted
 * {@link MatchResult} for the pair when one exists.
 */
@Service
@RequiredArgsConstructor
public class ShortlistService {

    /** Default inclusive match-fit floor for auto-shortlist when the caller supplies none. */
    static final int DEFAULT_MIN_FIT = 75;

    private final MatchResultRepository matchResultRepository;
    private final ShortlistDecisionRepository shortlistDecisionRepository;
    private final ApplicationService applicationService;
    private final CampusAccessService campusAccessService;
    private final DriveRepository driveRepository;
    private final EligibilityService eligibilityService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ShortlistDecisionResponse decide(String driveId, String studentId, ShortlistRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());

        String raw = request == null || request.decision() == null ? "SHORTLIST" : request.decision();
        boolean reject = "REJECT".equals(raw.trim().toUpperCase(Locale.ROOT));

        // Hard gate: a manual SHORTLIST re-runs the deterministic eligibility check rather than
        // trusting the application-time result; REJECT stays allowed regardless of eligibility.
        if (!reject) {
            EligibilityResponse eligibility = eligibilityService.evaluate(driveId, studentId);
            if (!eligibility.eligible()) {
                throw new NotEligibleException(eligibility.failureReasons());
            }
        }

        String matchResultId = matchResultRepository
            .findFirstByDriveIdAndStudentIdOrderByComputedAtDesc(driveId, studentId)
            .map(MatchResult::getId)
            .orElse(null);

        ShortlistDecision saved = shortlistDecisionRepository.save(ShortlistDecision.builder()
            .id("shortlist_" + UUID.randomUUID())
            .matchResultId(matchResultId)
            .driveId(driveId)
            .studentId(studentId)
            .decidedBy(user.getUsername())
            .decision(reject ? "REJECT" : "SHORTLIST")
            .reason(request == null ? null : request.reason())
            .decidedAt(OffsetDateTime.now())
            .build());

        ApplicationStatus target = reject ? ApplicationStatus.REJECTED : ApplicationStatus.SHORTLISTED;
        String applicationStatus = applicationService.advance(studentId, driveId, target, user.getUsername())
            .map(Application::getStatus)
            .map(Enum::name)
            .orElse(null);

        // Tell the student they were shortlisted / not selected (in-app notification via the consumer).
        eventPublisher.publishEvent(ShortlistDecidedEvent.of(
            drive.getCampusId(), driveId, studentId, reject, saved.getDecidedAt()));

        return ShortlistDecisionResponse.of(saved, applicationStatus);
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can shortlist candidates");
        }
    }

    /**
     * Batch shortlist over a drive's scored applicants: eligibility is the hard gate (re-verified
     * server-side per student, never trusting a stored flag), then any eligible applicant whose
     * match fit is at or above {@code minFit} is shortlisted via {@link #decide}. Ineligible or
     * low-fit applicants are skipped and reported, so the deterministic eligibility rule check
     * always governs who can be shortlisted.
     */
    @Transactional
    public AutoShortlistResponse autoShortlist(String driveId, AutoShortlistRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());

        int minFit = request == null || request.minFit() == null ? DEFAULT_MIN_FIT : request.minFit();
        Set<String> only = request == null || request.studentIds() == null
            ? null
            : new HashSet<>(request.studentIds());

        List<AutoShortlistResponse.Outcome> outcomes = new ArrayList<>();
        int shortlisted = 0;
        int skippedIneligible = 0;
        int skippedLowFit = 0;

        for (MatchResult match : matchResultRepository.findByDriveIdOrderByOverallFitDesc(driveId)) {
            String studentId = match.getStudentId();
            if (only != null && !only.contains(studentId)) {
                continue;
            }
            // Hard gate: re-run the deterministic eligibility check rather than trust the stored flag.
            boolean eligible = eligibilityService.evaluate(driveId, studentId).eligible();
            if (!eligible) {
                skippedIneligible++;
                outcomes.add(new AutoShortlistResponse.Outcome(
                    studentId, "SKIPPED_INELIGIBLE", match.getOverallFit(), false));
                continue;
            }
            if (match.getOverallFit() < minFit) {
                skippedLowFit++;
                outcomes.add(new AutoShortlistResponse.Outcome(
                    studentId, "SKIPPED_LOW_FIT", match.getOverallFit(), true));
                continue;
            }
            decide(driveId, studentId, new ShortlistRequest("SHORTLIST",
                "Auto-shortlisted: fit " + match.getOverallFit() + "% >= " + minFit + "% and eligible"));
            shortlisted++;
            outcomes.add(new AutoShortlistResponse.Outcome(
                studentId, "SHORTLISTED", match.getOverallFit(), true));
        }

        return new AutoShortlistResponse(
            driveId, minFit, shortlisted, skippedIneligible, skippedLowFit, outcomes);
    }
}
