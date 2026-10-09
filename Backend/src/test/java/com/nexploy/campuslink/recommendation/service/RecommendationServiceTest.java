package com.nexploy.campuslink.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.dto.EligibilityResponse.EligibilityCheck;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.matching.entity.MatchResult;
import com.nexploy.campuslink.matching.entity.ShortlistDecision;
import com.nexploy.campuslink.matching.repository.MatchResultRepository;
import com.nexploy.campuslink.matching.repository.ShortlistDecisionRepository;
import com.nexploy.campuslink.matching.service.MatchingService;
import com.nexploy.campuslink.recommendation.dto.CandidateRecommendationResponse;
import com.nexploy.campuslink.recommendation.dto.RecommendationResponse;
import com.nexploy.campuslink.recommendation.dto.RecommendationType;
import com.nexploy.campuslink.recommendation.entity.RecommendationExplanation;
import com.nexploy.campuslink.recommendation.explain.RecommendationExplainer;
import com.nexploy.campuslink.recommendation.explain.RecommendationExplainer.ExplanationResult;
import com.nexploy.campuslink.recommendation.repository.RecommendationExplanationRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for the recommendation orchestration. The explainer (LLM) is always mocked — no real
 * model is called. Covers the four type derivations, serve-persisted vs regenerate, staleness
 * regeneration, deterministic fallback on an absent/throwing explainer, the ML-unreachable degrade,
 * and the student-own / manager access matrix.
 */
class RecommendationServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final DriveRepository driveRepository = mock(DriveRepository.class);
    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final MatchResultRepository matchResultRepository = mock(MatchResultRepository.class);
    private final ShortlistDecisionRepository shortlistDecisionRepository =
        mock(ShortlistDecisionRepository.class);
    private final RecommendationExplanationRepository explanationRepository =
        mock(RecommendationExplanationRepository.class);
    private final EligibilityService eligibilityService = mock(EligibilityService.class);
    private final MatchingService matchingService = mock(MatchingService.class);
    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final RecommendationExplainer explainer = mock(RecommendationExplainer.class);

    private RecommendationService serviceWith(RecommendationExplainer wired) {
        @SuppressWarnings("unchecked")
        ObjectProvider<RecommendationExplainer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(wired);
        return new RecommendationService(applicationRepository, driveRepository, studentRepository,
            matchResultRepository, shortlistDecisionRepository, explanationRepository,
            eligibilityService, matchingService, applicationService, campusAccessService,
            provider, new ObjectMapper());
    }

    // ---- Type derivation --------------------------------------------------------

    @Test
    void notEligibleWhenMatchFailsEligibility() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(match(false, 20), eligible(false, "CGPA below cutoff")), false);

        assertThat(resp.recommendationType()).isEqualTo(RecommendationType.NOT_ELIGIBLE);
        assertThat(resp.eligibilityPassed()).isFalse();
    }

    @Test
    void eligibleWhenPassedAndNoShortlistDecision() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(shortlistDecisionRepository
            .findFirstByDriveIdAndStudentIdOrderByDecidedAtDesc("drv_1", "stu_1"))
            .thenReturn(Optional.empty());

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(match(true, 82), eligible(true)), false);

        assertThat(resp.recommendationType()).isEqualTo(RecommendationType.ELIGIBLE);
    }

    @Test
    void shortlistedWhenLatestDecisionIsShortlist() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(shortlistDecisionRepository
            .findFirstByDriveIdAndStudentIdOrderByDecidedAtDesc("drv_1", "stu_1"))
            .thenReturn(Optional.of(decision("SHORTLIST")));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(match(true, 90), eligible(true)), false);

        assertThat(resp.recommendationType()).isEqualTo(RecommendationType.SHORTLISTED);
    }

    @Test
    void notShortlistedWhenLatestDecisionIsReject() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(shortlistDecisionRepository
            .findFirstByDriveIdAndStudentIdOrderByDecidedAtDesc("drv_1", "stu_1"))
            .thenReturn(Optional.of(decision("REJECT")));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(match(true, 70), eligible(true)), false);

        assertThat(resp.recommendationType()).isEqualTo(RecommendationType.NOT_SHORTLISTED);
    }

    // ---- Serve persisted vs regenerate -----------------------------------------

    @Test
    void servesPersistedExplanationWithoutCallingModelWhenFresh() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        OffsetDateTime computedAt = OffsetDateTime.now();
        MatchResult m = match(true, 80);
        m.setComputedAt(computedAt);
        when(explanationRepository.findByMatchResultId("mr_1"))
            .thenReturn(Optional.of(persisted("cached text", computedAt, "OK")));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(m, eligible(true)), false);

        assertThat(resp.aiExplanation()).isEqualTo("cached text");
        assertThat(resp.explanationStatus()).isEqualTo("OK");
        verify(explainer, never()).explain(any());
    }

    @Test
    void regenerateForcesFreshGenerationEvenWhenPersistedIsFresh() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        OffsetDateTime computedAt = OffsetDateTime.now();
        MatchResult m = match(true, 80);
        m.setComputedAt(computedAt);
        when(explanationRepository.findByMatchResultId("mr_1"))
            .thenReturn(Optional.of(persisted("stale cached", computedAt, "OK")));
        when(explainer.explain(any())).thenReturn(new ExplanationResult("regenerated", List.of("x")));
        when(explanationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(m, eligible(true)), true);

        assertThat(resp.aiExplanation()).isEqualTo("regenerated");
        assertThat(resp.explanationStatus()).isEqualTo("OK");
        verify(explainer).explain(any());
    }

    @Test
    void regeneratesWhenPersistedIsStaleAgainstReScoredEvidence() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        MatchResult m = match(true, 80);
        m.setComputedAt(OffsetDateTime.now());
        // Persisted explanation was generated against an older computedAt -> stale.
        when(explanationRepository.findByMatchResultId("mr_1"))
            .thenReturn(Optional.of(persisted("old", OffsetDateTime.now().minusDays(1), "OK")));
        when(explainer.explain(any())).thenReturn(new ExplanationResult("fresh", List.of()));
        when(explanationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(m, eligible(true)), false);

        assertThat(resp.aiExplanation()).isEqualTo("fresh");
        verify(explainer).explain(any());
    }

    // ---- Fallback ---------------------------------------------------------------

    @Test
    void fallsBackToDeterministicTextWhenNoExplainerWired() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(explanationRepository.findByMatchResultId("mr_1")).thenReturn(Optional.empty());
        when(explanationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RecommendationResponse resp = serviceWith(null)  // provider yields no explainer
            .forApplication(setupApplication(match(true, 75), eligible(true)), false);

        assertThat(resp.explanationStatus()).isEqualTo("FALLBACK");
        assertThat(resp.aiExplanation()).isNotBlank();
    }

    @Test
    void fallsBackToDeterministicTextWhenExplainerThrows() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(explanationRepository.findByMatchResultId("mr_1")).thenReturn(Optional.empty());
        when(explanationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(explainer.explain(any())).thenThrow(new IllegalStateException("AI unavailable"));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(match(true, 75), eligible(true)), false);

        assertThat(resp.explanationStatus()).isEqualTo("FALLBACK");
        assertThat(resp.aiExplanation()).isNotBlank();
    }

    // ---- ML-unreachable degrade -------------------------------------------------

    @Test
    void degradesToEligibilityOnlyWhenRankerUnreachable() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        String appId = setupApplicationNoMatch(eligible(false, "No match possible"));
        // No persisted match and the lazy populate blows up.
        when(matchResultRepository.findFirstByDriveIdAndStudentIdOrderByComputedAtDesc("drv_1", "stu_1"))
            .thenReturn(Optional.empty());
        doThrow(new RuntimeException("ranker down")).when(matchingService)
            .matchesForDrive(anyString(), anyString());

        RecommendationResponse resp = serviceWith(explainer).forApplication(appId, false);

        assertThat(resp.overallFit()).isNull();
        assertThat(resp.breakdown()).isNull();
        assertThat(resp.computedAt()).isNull();
        assertThat(resp.aiExplanation()).isNull();
        assertThat(resp.recommendationType()).isEqualTo(RecommendationType.NOT_ELIGIBLE);
        verify(explainer, never()).explain(any());
    }

    // ---- Security ---------------------------------------------------------------

    @Test
    void owningStudentMayReadOwnRecommendation() {
        authenticate(user(UserRole.STUDENT, "campus_a", "stu@e.example"));
        when(studentRepository.findByEmail("stu@e.example")).thenReturn(Optional.of(student("stu_1")));
        MatchResult m = match(true, 80);
        m.setComputedAt(OffsetDateTime.now());
        when(explanationRepository.findByMatchResultId("mr_1"))
            .thenReturn(Optional.of(persisted("ok", m.getComputedAt(), "OK")));

        RecommendationResponse resp = serviceWith(explainer)
            .forApplication(setupApplication(m, eligible(true)), false);

        assertThat(resp.studentId()).isEqualTo("stu_1");
        // A student owner never triggers the manager guard.
        verify(applicationService, never()).requireApplicationManager(any(), any());
    }

    @Test
    void nonOwnerStudentIsDeniedViaManagerGuard() {
        authenticate(user(UserRole.STUDENT, "campus_a", "other@e.example"));
        when(studentRepository.findByEmail("other@e.example"))
            .thenReturn(Optional.of(student("stu_other")));
        doThrow(new AccessDeniedException("not a manager")).when(applicationService)
            .requireApplicationManager(any(), any());

        String appId = setupApplication(match(true, 80), eligible(true));

        assertThatThrownBy(() -> serviceWith(explainer).forApplication(appId, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ---- Drive list serves persisted only --------------------------------------

    @Test
    void driveListIsDeterministicallyOrderedAndNeverGenerates() {
        authenticate(user(UserRole.OFFICER, "campus_a"));
        when(driveRepository.findById("drv_1")).thenReturn(Optional.of(drive()));
        MatchResult top = match(true, 90);
        top.setId("mr_top");
        top.setStudentId("stu_top");
        MatchResult low = match(false, 40);
        low.setId("mr_low");
        low.setStudentId("stu_low");
        when(matchResultRepository.findByDriveIdOrderByOverallFitDesc("drv_1"))
            .thenReturn(List.of(top, low));
        when(explanationRepository.findByMatchResultIdIn(List.of("mr_top", "mr_low")))
            .thenReturn(List.of());
        when(shortlistDecisionRepository
            .findFirstByDriveIdAndStudentIdOrderByDecidedAtDesc(anyString(), anyString()))
            .thenReturn(Optional.empty());

        List<CandidateRecommendationResponse> out = serviceWith(explainer).forDrive("drv_1");

        assertThat(out).extracting(CandidateRecommendationResponse::studentId)
            .containsExactly("stu_top", "stu_low");
        assertThat(out.get(0).aiExplanation()).isNull();  // persisted-only; none stored
        verify(explainer, never()).explain(any());
    }

    // ---- Fixtures ---------------------------------------------------------------

    /** Wires a stored application + drive + match + eligibility for the (drv_1, stu_1) pair. */
    private String setupApplication(MatchResult m, EligibilityResponse elig) {
        Application app = Application.builder().id("app_1").studentId("stu_1").driveId("drv_1").build();
        when(applicationRepository.findById("app_1")).thenReturn(Optional.of(app));
        when(driveRepository.findById("drv_1")).thenReturn(Optional.of(drive()));
        when(eligibilityService.evaluate("drv_1", "stu_1")).thenReturn(elig);
        when(matchResultRepository.findFirstByDriveIdAndStudentIdOrderByComputedAtDesc("drv_1", "stu_1"))
            .thenReturn(Optional.of(m));
        // Default: an upsert echoes the saved row (individual tests override the explainer/persisted state).
        when(explanationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return "app_1";
    }

    private String setupApplicationNoMatch(EligibilityResponse elig) {
        Application app = Application.builder().id("app_1").studentId("stu_1").driveId("drv_1").build();
        when(applicationRepository.findById("app_1")).thenReturn(Optional.of(app));
        when(driveRepository.findById("drv_1")).thenReturn(Optional.of(drive()));
        when(eligibilityService.evaluate("drv_1", "stu_1")).thenReturn(elig);
        return "app_1";
    }

    private MatchResult match(boolean eligibilityPassed, int fit) {
        return MatchResult.builder()
            .id("mr_1").campusId("campus_a").cycleId("current").studentId("stu_1").driveId("drv_1")
            .overallFit(fit).eligibilityPassed(eligibilityPassed)
            .breakdownJson("{\"eligibility\":100,\"skills\":80,\"semanticMatch\":80,\"projects\":70,"
                + "\"interview\":60,\"readiness\":75,\"academics\":82}")
            .whySelectedJson("[\"Strong skills\"]").weakOrMissingJson("[\"Few projects\"]")
            .modelVersion("m1").computedAt(OffsetDateTime.now()).build();
    }

    private EligibilityResponse eligible(boolean passed, String... reasons) {
        return new EligibilityResponse("stu_1", "drv_1", passed,
            List.of(new EligibilityCheck("CGPA", passed, "cutoff 7.0")), List.of(reasons));
    }

    private RecommendationExplanation persisted(String text, OffsetDateTime sourceComputedAt, String status) {
        return RecommendationExplanation.builder()
            .id("recexpl_1").matchResultId("mr_1").aiExplanation(text)
            .improvementAreasJson("[]").status(status).aiModelVersion("rec-explain-v1")
            .sourceComputedAt(sourceComputedAt).generatedAt(OffsetDateTime.now()).build();
    }

    private ShortlistDecision decision(String d) {
        return ShortlistDecision.builder().id("sl_1").matchResultId("mr_1").driveId("drv_1")
            .studentId("stu_1").decidedBy("officer").decision(d).decidedAt(OffsetDateTime.now()).build();
    }

    private Drive drive() {
        return Drive.builder().id("drv_1").campusId("campus_a").recruiterId("rec_1").build();
    }

    private Student student(String id) {
        return Student.builder().id(id).campusId("campus_a").name("Demo").email("stu@e.example")
            .rollNumber("D-1").branch("CSE").branchCode("CS").graduationYear(2026)
            .cgpa(new BigDecimal("8.2")).backlogs(0).readinessScore(80).placementStatus("ACTIVE").build();
    }

    private UserAccount user(UserRole role, String campusId) {
        return user(role, campusId, "user-" + role);
    }

    private UserAccount user(UserRole role, String campusId, String username) {
        return UserAccount.builder().username(username).password("{noop}s")
            .campusId(campusId).role(role).enabled(true).build();
    }

    private void authenticate(UserAccount u) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }
}
