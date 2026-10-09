package com.nexploy.campuslink.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.application.service.ApplicationService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.EligibilityResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.matching.dto.MatchResponse.MatchBreakdown;
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
import com.nexploy.campuslink.recommendation.explain.RecommendationExplainer.BreakdownFactor;
import com.nexploy.campuslink.recommendation.explain.RecommendationExplainer.ExplanationResult;
import com.nexploy.campuslink.recommendation.explain.RecommendationExplainer.RecommendationFacts;
import com.nexploy.campuslink.recommendation.repository.RecommendationExplanationRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the explainable recommendation for an application or a drive. All numbers, eligibility, and
 * the {@link RecommendationType} are deterministic (reusing the existing matching + eligibility engines);
 * the LLM only supplies human-readable text via {@link RecommendationExplainer}, and any AI failure
 * degrades to a deterministic fallback so the endpoints never break. The AI text is persisted separately
 * from the evidence and served from cache — the LLM is called only on first generation, an explicit
 * regenerate, or when the underlying evidence has been re-scored (staleness).
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final String STATUS_OK = "OK";
    private static final String STATUS_FALLBACK = "FALLBACK";
    private static final String EXPLANATION_MODEL_VERSION = "rec-explain-v1";
    /** Placement cycle token used when lazily populating matches (mirrors the /matches default). */
    private static final String DEFAULT_CYCLE = "current";

    private final ApplicationRepository applicationRepository;
    private final DriveRepository driveRepository;
    private final StudentRepository studentRepository;
    private final MatchResultRepository matchResultRepository;
    private final ShortlistDecisionRepository shortlistDecisionRepository;
    private final RecommendationExplanationRepository explanationRepository;
    private final EligibilityService eligibilityService;
    private final MatchingService matchingService;
    private final ApplicationService applicationService;
    private final CampusAccessService campusAccessService;
    private final ObjectProvider<RecommendationExplainer> explainerProvider;
    private final ObjectMapper objectMapper;

    // ---- Application-keyed recommendation ---------------------------------------

    /**
     * The explainable recommendation for one application. The owning student, or a manager of the
     * drive (officer/admin/owning recruiter), may read it. {@code refresh=true} forces the AI text to
     * be regenerated; otherwise a fresh persisted copy is served without calling the model.
     */
    @Transactional
    public RecommendationResponse forApplication(String applicationId, boolean refresh) {
        UserAccount user = campusAccessService.currentUser();
        Application application = applicationRepository.findById(applicationId)
            .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));
        Drive drive = driveRepository.findById(application.getDriveId())
            .orElseThrow(() -> new DriveNotFoundException(application.getDriveId()));

        // Security: the owning student, else a manager of the drive. A non-owner student is denied here.
        boolean owner = studentRepository.findByEmail(user.getUsername())
            .map(s -> s.getId().equals(application.getStudentId()))
            .orElse(false);
        if (!owner) {
            applicationService.requireApplicationManager(user, drive);
        }

        String driveId = application.getDriveId();
        String studentId = application.getStudentId();
        EligibilityResponse eligibility = eligibilityService.evaluate(driveId, studentId);
        MatchResult match = resolveMatch(driveId, studentId);

        if (match == null) {
            // Degraded (e.g. ML ranker unreachable): eligibility-only evidence, no score, no AI text.
            RecommendationType type = deriveType(eligibility.eligible(), driveId, studentId);
            return new RecommendationResponse(applicationId, studentId, driveId, type,
                eligibility.eligible(), eligibility.checks(), null, null, List.of(), List.of(),
                null, List.of(), null, null);
        }

        RecommendationType type = deriveType(match.isEligibilityPassed(), driveId, studentId);
        MatchBreakdown breakdown = readBreakdown(match.getBreakdownJson());
        List<String> whySelected = readList(match.getWhySelectedJson());
        List<String> weakOrMissing = readList(match.getWeakOrMissingJson());
        RecommendationExplanation explanation =
            resolveExplanation(match, type, eligibility, breakdown, whySelected, weakOrMissing, refresh);

        return new RecommendationResponse(applicationId, studentId, driveId, type,
            match.isEligibilityPassed(), eligibility.checks(), match.getOverallFit(), breakdown,
            whySelected, weakOrMissing, explanation.getAiExplanation(),
            readList(explanation.getImprovementAreasJson()), explanation.getStatus(),
            match.getComputedAt());
    }

    // ---- Drive-keyed ranked candidate recommendations ---------------------------

    /**
     * Ranked candidate recommendations for a drive (managers only). Order is the deterministic fit
     * ranking; the LLM never reorders. Serves only already-persisted AI text — never generates on load.
     */
    @Transactional
    public List<CandidateRecommendationResponse> forDrive(String driveId) {
        UserAccount user = campusAccessService.currentUser();
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        applicationService.requireApplicationManager(user, drive);

        List<MatchResult> rows = matchResultRepository.findByDriveIdOrderByOverallFitDesc(driveId);
        if (rows.isEmpty()) {
            // Best-effort deterministic populate (no LLM). If the ranker is unreachable, serve what we have.
            try {
                matchingService.matchesForDrive(driveId, DEFAULT_CYCLE);
                rows = matchResultRepository.findByDriveIdOrderByOverallFitDesc(driveId);
            } catch (RuntimeException e) {
                log.warn("Could not populate matches for drive {}; serving persisted only. cause={}",
                    driveId, e.toString());
            }
        }

        Map<String, RecommendationExplanation> byMatchId = rows.isEmpty()
            ? Map.of()
            : explanationRepository.findByMatchResultIdIn(rows.stream().map(MatchResult::getId).toList())
                .stream().collect(Collectors.toMap(RecommendationExplanation::getMatchResultId,
                    Function.identity(), (a, b) -> a));

        List<CandidateRecommendationResponse> out = new ArrayList<>();
        for (MatchResult match : rows) {
            RecommendationType type = deriveType(match.isEligibilityPassed(), driveId, match.getStudentId());
            RecommendationExplanation explanation = byMatchId.get(match.getId());
            out.add(new CandidateRecommendationResponse(
                match.getStudentId(), match.getOverallFit(), type, match.isEligibilityPassed(),
                readList(match.getWhySelectedJson()), readList(match.getWeakOrMissingJson()),
                explanation == null ? null : explanation.getAiExplanation(),
                explanation == null ? null : explanation.getStatus()));
        }
        return out;
    }

    // ---- Deterministic recommendation type --------------------------------------

    /**
     * Derives the recommendation type from the eligibility verdict and the latest officer shortlist
     * decision. Reads the shortlist audit only; the official application funnel is never touched.
     */
    private RecommendationType deriveType(boolean eligibilityPassed, String driveId, String studentId) {
        if (!eligibilityPassed) {
            return RecommendationType.NOT_ELIGIBLE;
        }
        return shortlistDecisionRepository
            .findFirstByDriveIdAndStudentIdOrderByDecidedAtDesc(driveId, studentId)
            .map(ShortlistDecision::getDecision)
            .map(d -> "REJECT".equalsIgnoreCase(d)
                ? RecommendationType.NOT_SHORTLISTED
                : RecommendationType.SHORTLISTED)
            .orElse(RecommendationType.ELIGIBLE);
    }

    // ---- Match resolution (lazy populate, degrade on failure) -------------------

    /**
     * The latest persisted {@link MatchResult} for the pair, lazily populating it once via the matching
     * engine if none exists. Returns {@code null} (degrade to eligibility-only) if the ranker is
     * unreachable or the pair cannot be scored, so a page load never errors.
     */
    private MatchResult resolveMatch(String driveId, String studentId) {
        Optional<MatchResult> existing =
            matchResultRepository.findFirstByDriveIdAndStudentIdOrderByComputedAtDesc(driveId, studentId);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            matchingService.matchesForDrive(driveId, DEFAULT_CYCLE);
        } catch (RuntimeException e) {
            log.warn("Could not populate match for drive {} student {}; degrading to eligibility-only. "
                + "cause={}", driveId, studentId, e.toString());
            return null;
        }
        return matchResultRepository
            .findFirstByDriveIdAndStudentIdOrderByComputedAtDesc(driveId, studentId)
            .orElse(null);
    }

    // ---- Explanation: serve persisted, regenerate on refresh/staleness ----------

    private RecommendationExplanation resolveExplanation(
        MatchResult match, RecommendationType type, EligibilityResponse eligibility,
        MatchBreakdown breakdown, List<String> whySelected, List<String> weakOrMissing, boolean refresh) {
        Optional<RecommendationExplanation> existing =
            explanationRepository.findByMatchResultId(match.getId());
        boolean fresh = existing.isPresent()
            && !refresh
            && existing.get().getSourceComputedAt() != null
            && match.getComputedAt() != null
            && existing.get().getSourceComputedAt().isEqual(match.getComputedAt());
        if (fresh) {
            return existing.get();
        }
        return generateAndStore(match, type, eligibility, breakdown, whySelected, weakOrMissing,
            existing.orElse(null));
    }

    private RecommendationExplanation generateAndStore(
        MatchResult match, RecommendationType type, EligibilityResponse eligibility,
        MatchBreakdown breakdown, List<String> whySelected, List<String> weakOrMissing,
        RecommendationExplanation existing) {
        RecommendationFacts facts = new RecommendationFacts(type, match.isEligibilityPassed(),
            eligibility.failureReasons(), match.getOverallFit(), breakdownFactors(breakdown),
            whySelected, weakOrMissing);

        ExplanationResult result = null;
        String status = STATUS_FALLBACK;
        RecommendationExplainer explainer = explainerProvider.getIfAvailable();
        if (explainer != null) {
            try {
                result = explainer.explain(facts);
                status = STATUS_OK;
            } catch (RuntimeException e) {
                // LLM disabled mid-flight, API error, timeout, quota, prompt-injection guard, etc.
                // Log the full root cause (not just the wrapper) so real failures are diagnosable;
                // attach the stack at debug for deeper inspection without noise at WARN.
                log.warn("Recommendation explainer failed; using deterministic fallback. cause={}",
                    rootCause(e));
                log.debug("Recommendation explainer failure detail", e);
                result = null;
            }
        }
        if (result == null || result.explanation() == null || result.explanation().isBlank()) {
            result = deterministicFallback(type, eligibility, whySelected, weakOrMissing);
            status = STATUS_FALLBACK;
        }

        RecommendationExplanation row = existing != null ? existing
            : RecommendationExplanation.builder().id("recexpl_" + UUID.randomUUID()).build();
        row.setMatchResultId(match.getId());
        row.setAiExplanation(result.explanation());
        row.setImprovementAreasJson(writeJson(result.improvementAreas()));
        row.setStatus(status);
        row.setAiModelVersion(STATUS_OK.equals(status) ? EXPLANATION_MODEL_VERSION : null);
        row.setSourceComputedAt(match.getComputedAt());
        row.setGeneratedAt(OffsetDateTime.now());
        return explanationRepository.save(row);
    }

    /** Deterministic explanation used whenever AI is disabled/unavailable — grounded only in evidence. */
    private ExplanationResult deterministicFallback(RecommendationType type,
        EligibilityResponse eligibility, List<String> whySelected, List<String> weakOrMissing) {
        StringBuilder text = new StringBuilder();
        switch (type) {
            case NOT_ELIGIBLE -> text.append("Not eligible: ")
                .append(eligibility.failureReasons().isEmpty()
                    ? "did not meet the drive's hard criteria."
                    : String.join("; ", eligibility.failureReasons()) + ".");
            case SHORTLISTED -> text.append("Shortlisted. ");
            case NOT_SHORTLISTED -> text.append("Reviewed and not shortlisted. ");
            case ELIGIBLE -> text.append("Eligible and awaiting a shortlisting decision. ");
        }
        if (type != RecommendationType.NOT_ELIGIBLE) {
            if (!whySelected.isEmpty()) {
                text.append("Strengths: ").append(String.join("; ", whySelected)).append(". ");
            }
            if (!weakOrMissing.isEmpty()) {
                text.append("Areas to improve: ").append(String.join("; ", weakOrMissing)).append(".");
            }
        }
        List<String> improvements = type == RecommendationType.NOT_ELIGIBLE
            ? eligibility.failureReasons()
            : weakOrMissing;
        return new ExplanationResult(text.toString().trim(), improvements);
    }

    private List<BreakdownFactor> breakdownFactors(MatchBreakdown b) {
        if (b == null) {
            return List.of();
        }
        return List.of(
            new BreakdownFactor("skills", b.skills()),
            new BreakdownFactor("semanticMatch", b.semanticMatch()),
            new BreakdownFactor("projects", b.projects()),
            new BreakdownFactor("interview", b.interview()),
            new BreakdownFactor("readiness", b.readiness()),
            new BreakdownFactor("academics", b.academics()));
    }

    // ---- JSON helpers -----------------------------------------------------------

    private MatchBreakdown readBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MatchBreakdown.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * Unwrap an exception to its most useful description: the deepest non-null cause plus its message,
     * with the wrapper class prefixed. The explainer wraps API/parse failures in an IllegalStateException
     * whose {@code toString()} hides the real reason (auth, model, quota, JSON parse), so surface the
     * whole chain for diagnosis.
     */
    private static String rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root == t) {
            return t.toString();
        }
        return t.getClass().getSimpleName() + " -> " + root;
    }

    // Suppress unused-import style warnings for Student/Locale kept for clarity in future edits.
    @SuppressWarnings("unused")
    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unused")
    private static String studentIdOf(Student s) {
        return s == null ? null : s.getId();
    }
}
