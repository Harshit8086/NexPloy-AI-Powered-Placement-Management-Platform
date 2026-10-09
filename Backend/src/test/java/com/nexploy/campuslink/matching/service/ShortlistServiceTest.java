package com.nexploy.campuslink.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.application.exception.NotEligibleException;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.matching.dto.AutoShortlistRequest;
import com.nexploy.campuslink.matching.dto.AutoShortlistResponse;
import com.nexploy.campuslink.matching.dto.ShortlistRequest;
import com.nexploy.campuslink.matching.entity.MatchResult;
import com.nexploy.campuslink.matching.entity.ShortlistDecision;
import com.nexploy.campuslink.matching.repository.MatchResultRepository;
import com.nexploy.campuslink.matching.repository.ShortlistDecisionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ShortlistServiceTest {

    private final MatchResultRepository matchResultRepository =
        org.mockito.Mockito.mock(MatchResultRepository.class);
    private final ShortlistDecisionRepository shortlistDecisionRepository =
        org.mockito.Mockito.mock(ShortlistDecisionRepository.class);
    private final ApplicationService applicationService =
        org.mockito.Mockito.mock(ApplicationService.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final DriveRepository driveRepository = org.mockito.Mockito.mock(DriveRepository.class);
    private final EligibilityService eligibilityService =
        org.mockito.Mockito.mock(EligibilityService.class);
    private final ApplicationEventPublisher eventPublisher =
        org.mockito.Mockito.mock(ApplicationEventPublisher.class);

    private final ShortlistService service = new ShortlistService(
        matchResultRepository, shortlistDecisionRepository, applicationService, campusAccessService,
        driveRepository, eligibilityService, eventPublisher);

    @Test
    void autoShortlistGatesOnEligibilityAndMinFit() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drive_demo")).thenReturn(Optional.of(drive("campus_demo")));
        when(matchResultRepository.findByDriveIdOrderByOverallFitDesc("drive_demo")).thenReturn(List.of(
            match("stu_hi", 92),   // eligible + above floor  -> shortlisted
            match("stu_lo", 60),   // eligible + below floor  -> skipped low fit
            match("stu_bad", 95))); // ineligible              -> skipped ineligible
        when(eligibilityService.evaluate("drive_demo", "stu_hi")).thenReturn(eligibility("stu_hi", true));
        when(eligibilityService.evaluate("drive_demo", "stu_lo")).thenReturn(eligibility("stu_lo", true));
        when(eligibilityService.evaluate("drive_demo", "stu_bad")).thenReturn(eligibility("stu_bad", false));
        when(shortlistDecisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicationService.advance(any(), any(), any(), any())).thenReturn(Optional.empty());

        AutoShortlistResponse res = service.autoShortlist("drive_demo", new AutoShortlistRequest(75, null));

        assertThat(res.shortlisted()).isEqualTo(1);
        assertThat(res.skippedLowFit()).isEqualTo(1);
        assertThat(res.skippedIneligible()).isEqualTo(1);
        assertThat(res.minFit()).isEqualTo(75);
        assertThat(res.outcomes()).extracting(AutoShortlistResponse.Outcome::result)
            .containsExactly("SHORTLISTED", "SKIPPED_LOW_FIT", "SKIPPED_INELIGIBLE");
        // Only the one qualifying candidate produces a persisted decision.
        verify(shortlistDecisionRepository).save(ArgumentMatchers.<ShortlistDecision>argThat(
            d -> d.getStudentId().equals("stu_hi") && "SHORTLIST".equals(d.getDecision())));
    }

    @Test
    void autoShortlistNeverShortlistsIneligibleEvenWhenFitIsHigh() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drive_demo")).thenReturn(Optional.of(drive("campus_demo")));
        when(matchResultRepository.findByDriveIdOrderByOverallFitDesc("drive_demo"))
            .thenReturn(List.of(match("stu_bad", 99)));
        when(eligibilityService.evaluate("drive_demo", "stu_bad")).thenReturn(eligibility("stu_bad", false));

        AutoShortlistResponse res = service.autoShortlist("drive_demo", new AutoShortlistRequest(50, null));

        assertThat(res.shortlisted()).isZero();
        assertThat(res.skippedIneligible()).isEqualTo(1);
        verify(shortlistDecisionRepository, never()).save(any());
    }

    @Test
    void autoShortlistRejectsNonOfficer() {
        authenticate(user(UserRole.STUDENT, "campus_demo"));

        assertThatThrownBy(() -> service.autoShortlist("drive_demo", new AutoShortlistRequest(75, null)))
            .isInstanceOf(AccessDeniedException.class);
        verify(shortlistDecisionRepository, never()).save(any());
    }

    @Test
    void decideShortlistReChecksEligibilityAndBlocksIneligible() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drive_demo")).thenReturn(Optional.of(drive("campus_demo")));
        when(eligibilityService.evaluate("drive_demo", "stu_bad")).thenReturn(eligibility("stu_bad", false));

        assertThatThrownBy(() -> service.decide("drive_demo", "stu_bad", new ShortlistRequest("SHORTLIST", null)))
            .isInstanceOf(NotEligibleException.class);
        verify(shortlistDecisionRepository, never()).save(any());
    }

    @Test
    void decideShortlistAllowsEligibleCandidate() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(driveRepository.findById("drive_demo")).thenReturn(Optional.of(drive("campus_demo")));
        when(eligibilityService.evaluate("drive_demo", "stu_hi")).thenReturn(eligibility("stu_hi", true));
        when(matchResultRepository.findFirstByDriveIdAndStudentIdOrderByComputedAtDesc("drive_demo", "stu_hi"))
            .thenReturn(Optional.empty());
        when(shortlistDecisionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicationService.advance(any(), any(), any(), any())).thenReturn(Optional.empty());

        service.decide("drive_demo", "stu_hi", new ShortlistRequest("SHORTLIST", null));

        verify(shortlistDecisionRepository).save(ArgumentMatchers.<ShortlistDecision>argThat(
            d -> d.getStudentId().equals("stu_hi") && "SHORTLIST".equals(d.getDecision())));
    }

    private MatchResult match(String studentId, int overallFit) {
        return MatchResult.builder().id("mr_" + studentId).campusId("campus_demo").cycleId("current")
            .studentId(studentId).driveId("drive_demo").overallFit(overallFit).eligibilityPassed(true)
            .build();
    }

    private EligibilityResponse eligibility(String studentId, boolean eligible) {
        return new EligibilityResponse(studentId, "drive_demo", eligible, List.of(), List.of());
    }

    private Drive drive(String campusId) {
        return Drive.builder().id("drive_demo").campusId(campusId).recruiterId("rec_1")
            .companyName("Acme").role("SDE").location("Remote").packageLpa(new BigDecimal("12.0"))
            .minimumCgpa(new BigDecimal("7.0")).maximumBacklogs(0).graduationYear(2026).build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder().username("user-" + role).password("{noop}s")
            .campusId(campusId).role(role).enabled(true).build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
