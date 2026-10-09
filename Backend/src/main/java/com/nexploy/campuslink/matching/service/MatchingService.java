package com.nexploy.campuslink.matching.service;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.drive.dto.DriveResponse;
import com.nexploy.campuslink.drive.entity.Drive;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.repository.DriveRepository;
import com.nexploy.campuslink.drive.service.DriveService;
import com.nexploy.campuslink.drive.service.EligibilityService;
import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewResult;
import com.nexploy.campuslink.interview.repository.InterviewRepository;
import com.nexploy.campuslink.matching.client.MlMatchingClient;
import com.nexploy.campuslink.matching.client.MlMatchingClient.Candidate;
import com.nexploy.campuslink.matching.client.MlMatchingClient.Features;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingRequest;
import com.nexploy.campuslink.matching.client.MlMatchingClient.MatchingResult;
import com.nexploy.campuslink.matching.client.MlMatchingClient.RankedCandidate;
import com.nexploy.campuslink.matching.dto.MatchResponse;
import com.nexploy.campuslink.matching.dto.MatchResponse.MatchBreakdown;
import com.nexploy.campuslink.matching.dto.RankRequestBody;
import com.nexploy.campuslink.matching.entity.MatchResult;
import com.nexploy.campuslink.matching.repository.MatchResultRepository;
import com.nexploy.campuslink.placement.repository.PlacementCycleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.student.dto.StudentResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentCertificate;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.repository.StudentCertificateRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.repository.StudentSkillRepository;
import com.nexploy.campuslink.student.service.StudentService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates candidate ranking for a drive. Spring is the system of record and owns the
 * eligibility gate (SECURITY.md, spec section 5.3): for each candidate it resolves the student,
 * enforces campus access, computes deterministic eligibility, and reads governed readiness from
 * the record. Only fit features and the eligibility flag are forwarded to the stateless ML
 * service, which ranks but never decides eligibility. Ranking never drives an exclusion here.
 */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final DriveRepository driveRepository;
    private final StudentRepository studentRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final ApplicationRepository applicationRepository;
    private final EligibilityService eligibilityService;
    private final CampusAccessService campusAccessService;
    private final MlMatchingClient mlMatchingClient;
    private final DriveService driveService;
    private final StudentService studentService;
    private final EmbeddingService embeddingService;
    private final MatchResultRepository matchResultRepository;
    private final PlacementCycleRepository placementCycleRepository;
    private final ObjectMapper objectMapper;
    private final StudentCertificateRepository studentCertificateRepository;
    private final InterviewRepository interviewRepository;

    @Value("${ml.policy-version:policy-2026-01}")
    private String modelPolicyVersion;

    public MatchingResult rank(String driveId, String placementCycleId, RankRequestBody body) {
        UserAccount user = campusAccessService.currentUser();
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());

        List<Candidate> candidates = body.candidates().stream()
            .map(input -> toCandidate(user, driveId, input))
            .toList();

        MatchingRequest request = new MatchingRequest(
            UUID.randomUUID().toString(),
            drive.getCampusId(),
            placementCycleId,
            modelPolicyVersion,
            driveId,
            candidates,
            List.of()
        );
        return mlMatchingClient.rank(request);
    }

    private Candidate toCandidate(UserAccount user, String driveId, RankRequestBody.CandidateInput input) {
        Student student = studentRepository.findById(input.studentId())
            .orElseThrow(() -> new StudentNotFoundException(input.studentId()));
        // A candidate can only be ranked within a campus the caller may access.
        campusAccessService.requireAccess(user, student.getCampusId());

        // Deterministic eligibility gate, computed by Spring — the model consumes the result.
        boolean eligible = eligibilityService.evaluate(driveId, input.studentId()).eligible();
        Double readiness = student.getReadinessScore() == null ? null : student.getReadinessScore().doubleValue();

        return new Candidate(
            input.studentId(),
            eligible,
            new Features(
                input.requiredSkillCoverage(),
                input.semanticSimilarity(),
                input.projectRelevance(),
                readiness,
                input.interviewScore(),
                input.academics(),
                input.certificationRelevance(),
                // Governed, DB-derived like readiness: aggregated from the student's completed
                // past interviews, never supplied by the caller.
                toDouble(historicalInterviewPassRate(input.studentId(), driveId))
            ),
            List.of()
        );
    }

    /**
     * Explainable candidate matches for a drive, as the frontend {@code CandidateMatch} shape.
     * The candidate pool is the students who applied to the drive. Features are derived from
     * governed, stored data (skill coverage vs the drive's required skills, CGPA, readiness,
     * interview/projects from the readiness profile). Semantic similarity is computed DB-side with
     * pgvector: the drive's and each candidate's skill embeddings are ensured (lazily backfilled via
     * the ML service), then cosine similarity ({@code 1 - <=>}) is read from Postgres and passed to
     * the model as the {@code semanticSimilarity} feature, so the model uses our stored-vector value
     * rather than recomputing it. Spring still owns the eligibility gate.
     *
     * <p>Each computed match is persisted as a {@link MatchResult} (upsert by drive+student+
     * model version) so the fit score and explanation become a durable, auditable record.
     */
    @Transactional
    public List<MatchResponse> matchesForDrive(String driveId, String placementCycleId) {        UserAccount user = campusAccessService.currentUser();
        Drive drive = driveRepository.findById(driveId)
            .orElseThrow(() -> new DriveNotFoundException(driveId));
        campusAccessService.requireAccess(user, drive.getCampusId());

        DriveResponse driveView = driveService.toResponse(drive);
        List<String> requiredSkills = driveView.requiredSkills();
        // Ensure the drive's required-skills embedding is stored (pgvector) before computing similarity.
        embeddingService.ensureDriveEmbedding(driveId, requiredSkills);
        // Deep project-JD matching uses a second embedding pair: the drive's role/description text
        // vs each student's recorded project text. Ensure the drive side once here, like skills.
        embeddingService.ensureDriveDescriptionEmbedding(driveId, driveDescriptionTexts(driveView));

        List<String> studentIds = applicationRepository.findByDriveId(driveId).stream()
            .map(Application::getStudentId)
            .distinct()
            .toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }

        Map<String, Prep> prepared = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        for (String studentId : studentIds) {
            Student student = studentRepository.findById(studentId).orElse(null);
            if (student == null) {
                continue;
            }
            // An applicant whose home campus the caller cannot access (e.g. a cross-campus
            // application row) is skipped rather than denying the whole drive's match list —
            // one inaccessible applicant must not 403 the endpoint for every other candidate.
            if (!campusAccessService.canAccessCampus(user, student.getCampusId())) {
                continue;
            }
            StudentResponse view = studentService.toResponse(student);

            // Effective skill set for matching: the student's recorded skills (student_skills — the
            // authoritative "present ability" that My Skills edits) unioned with resume-derived
            // strengths. Using this rather than strengths alone means a student's recorded skills
            // drive skill coverage AND the semantic-similarity embedding.
            List<String> effectiveSkills = effectiveSkills(view.strengths(), studentId);

            boolean eligible = eligibilityService.evaluate(driveId, studentId).eligible();
            Integer coverage = skillCoverage(requiredSkills, effectiveSkills);
            Integer interview = view.assessments() == null ? null : view.assessments().mockInterview();
            Integer projects = view.breakdown() == null ? null : view.breakdown().projects();
            Integer academics = view.cgpa() == null ? null
                : (int) Math.round(view.cgpa().doubleValue() * 10);
            Integer readiness = view.readinessScore();

            // Certification relevance: the share of the drive's required skills backed by one of
            // the student's uploaded certificates (titles). Null when the student has none on
            // record — missing evidence is excluded from the score, never imputed as zero.
            List<String> certNames = studentCertificateRepository
                .findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(StudentCertificate::getTitle)
                .toList();
            Integer certificationRelevance = certNames.isEmpty()
                ? null
                : certificationRelevance(requiredSkills, certNames);

            // Historical interview performance: pass rate over the student's completed past
            // interviews, excluding this drive so its own outcomes never feed back into ranking.
            Integer historicalInterview = historicalInterviewPassRate(studentId, driveId);

            // DB-side semantic similarity (pgvector): ensure the student's embedding is stored, then
            // read cosine similarity against the drive. Null when either side has no skills — the
            // model then treats semantic similarity as missing rather than zero.
            List<String> skills = effectiveSkills;
            embeddingService.ensureStudentEmbedding(studentId, skills);
            Double semanticSimilarity = embeddingService.similarityPct(driveId, studentId);

            // Deep project-JD relevance (pgvector): the cosine between the student's project-text
            // embedding and the drive's description embedding. Null when either side has no text —
            // the readiness projects dimension below stands in as the fallback signal.
            embeddingService.ensureStudentProjectsEmbedding(studentId, studentProjectTexts(view));
            Double projectRelevance = embeddingService.projectRelevancePct(driveId, studentId);

            prepared.put(studentId, new Prep(
                eligible, coverage, interview, projects, academics, readiness, certificationRelevance,
                historicalInterview,
                presentSkills(requiredSkills, effectiveSkills),
                missingSkills(requiredSkills, effectiveSkills),
                view.cgpa()));

            candidates.add(new Candidate(studentId, eligible, new Features(
                toDouble(coverage),
                semanticSimilarity, // DB-side pgvector cosine; the model uses this rather than recomputing
                // DB-side project-JD cosine; falls back to the readiness projects dimension when
                // either embedding is absent (missing evidence degrades to the coarse signal, not zero).
                projectRelevance != null ? projectRelevance : toDouble(projects),
                toDouble(readiness),
                toDouble(interview),
                toDouble(academics),
                toDouble(certificationRelevance),
                toDouble(historicalInterview)),
                skills));
        }

        MatchingResult result = mlMatchingClient.rank(new MatchingRequest(
            UUID.randomUUID().toString(),
            drive.getCampusId(),
            placementCycleId,
            modelPolicyVersion,
            driveId,
            candidates,
            requiredSkills == null ? List.of() : requiredSkills));

        // Resolve the cycle only if it's a real placement_cycles row; otherwise leave null
        // (the param defaults to the free-text "current", which is not a cycle id).
        String resolvedCycleId = (placementCycleId != null
            && placementCycleRepository.existsById(placementCycleId)) ? placementCycleId : null;

        List<MatchResponse> matches = new ArrayList<>();
        for (RankedCandidate ranked : result.ranked()) {
            Prep prep = prepared.get(ranked.studentId());
            if (prep == null) {
                continue;
            }
            MatchResponse response = toMatchResponse(driveId, ranked, prep);
            matches.add(response);
            persistMatchResult(drive.getCampusId(), resolvedCycleId, response, prep.eligible());
        }
        return matches;
    }

    /** Upsert the durable match record keyed by (drive, student, model version). */
    private void persistMatchResult(String campusId, String cycleId, MatchResponse response,
                                    boolean eligible) {
        MatchResult row = matchResultRepository
            .findByDriveIdAndStudentIdAndModelVersion(response.driveId(), response.studentId(),
                modelPolicyVersion)
            .orElseGet(() -> MatchResult.builder().id("matchres_" + UUID.randomUUID()).build());
        row.setCampusId(campusId);
        row.setCycleId(cycleId);
        row.setStudentId(response.studentId());
        row.setDriveId(response.driveId());
        row.setOverallFit(response.overallFit());
        row.setEligibilityPassed(eligible);
        row.setBreakdownJson(writeJson(response.breakdown()));
        row.setWhySelectedJson(writeJson(response.whySelected()));
        row.setWeakOrMissingJson(writeJson(response.weakOrMissing()));
        row.setRecommendation(response.recommendation());
        row.setModelVersion(modelPolicyVersion);
        row.setComputedAt(OffsetDateTime.now());
        matchResultRepository.save(row);
    }

    private String writeJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private MatchResponse toMatchResponse(String driveId, RankedCandidate ranked, Prep prep) {
        int coverage = orZero(prep.coverage());
        int overallFit = ranked.fitScore() == null ? 0 : (int) Math.round(ranked.fitScore() * 100);
        // semanticMatch is the ML service's computed lexical-embedding cosine (falls back to skill
        // coverage only if the model returned none, e.g. a candidate with no listed skills).
        int semanticMatch = ranked.semanticSimilarity() == null
            ? coverage
            : (int) Math.round(ranked.semanticSimilarity());

        MatchBreakdown breakdown = new MatchBreakdown(
            prep.eligible() ? 100 : 0,
            coverage,
            semanticMatch,
            orZero(prep.projects()),
            orZero(prep.interview()),
            orZero(prep.readiness()),
            orZero(prep.academics()),
            orZero(prep.certificationRelevance()),
            orZero(prep.historicalInterview()));

        List<String> whySelected = new ArrayList<>();
        if (!prep.present().isEmpty()) {
            whySelected.add("Matches required skills: " + String.join(", ", prep.present()));
        }
        if (prep.cgpa() != null && prep.cgpa().doubleValue() >= 8.0) {
            whySelected.add("Strong academic record (CGPA " + prep.cgpa() + ")");
        }
        if (prep.readiness() != null && prep.readiness() >= 75) {
            whySelected.add("High placement readiness (" + prep.readiness() + ")");
        }

        List<String> weakOrMissing = new ArrayList<>(prep.missing().stream()
            .map(skill -> "Skill gap: " + skill)
            .toList());

        return new MatchResponse(
            "match_" + ranked.studentId() + "_" + driveId,
            ranked.studentId(),
            driveId,
            overallFit,
            breakdown,
            whySelected,
            weakOrMissing,
            recommendationFor(ranked.status(), overallFit));
    }

    /**
     * The student's effective skill set for matching: recorded skill labels from {@code student_skills}
     * (the authoritative "present ability" that the My Skills page edits) unioned with any
     * resume-derived strengths, deduped case-insensitively and preferring the recorded label's casing.
     * Prefer this over strengths alone so a student's recorded skills actually drive skill coverage
     * and the semantic-similarity embedding.
     */
    private List<String> effectiveSkills(List<String> strengths, String studentId) {
        Map<String, String> byLower = new LinkedHashMap<>();
        studentSkillRepository.findByStudentIdOrderBySkillAsc(studentId).forEach(s -> {
            if (s.getSkill() != null && !s.getSkill().isBlank()) {
                byLower.putIfAbsent(s.getSkill().toLowerCase(Locale.ROOT), s.getSkill());
            }
        });
        if (strengths != null) {
            strengths.forEach(s -> {
                if (s != null && !s.isBlank()) {
                    byLower.putIfAbsent(s.toLowerCase(Locale.ROOT), s);
                }
            });
        }
        return new ArrayList<>(byLower.values());
    }

    /**
     * The drive's description-side texts for deep project-JD matching: role, description and
     * required skills, blank entries dropped. The ML service joins and embeds them the same way it
     * embeds skills, so the stored vector stays comparable with the student's project-text vector.
     */
    private static List<String> driveDescriptionTexts(DriveResponse drive) {
        List<String> texts = new ArrayList<>();
        if (drive.role() != null && !drive.role().isBlank()) {
            texts.add(drive.role());
        }
        if (drive.description() != null && !drive.description().isBlank()) {
            texts.add(drive.description());
        }
        if (drive.requiredSkills() != null) {
            drive.requiredSkills().forEach(skill -> {
                if (skill != null && !skill.isBlank()) {
                    texts.add(skill);
                }
            });
        }
        return texts;
    }

    /**
     * The student's project texts for deep project-JD matching: each recorded project's name,
     * summary and stack. Empty when nothing is on record — the projects embedding then stays absent
     * and project relevance falls back to the readiness projects dimension.
     */
    private static List<String> studentProjectTexts(StudentResponse view) {
        if (view.projects() == null || view.projects().isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        view.projects().forEach(project -> {
            if (project.name() != null && !project.name().isBlank()) {
                texts.add(project.name());
            }
            if (project.summary() != null && !project.summary().isBlank()) {
                texts.add(project.summary());
            }
            if (project.stack() != null) {
                project.stack().forEach(entry -> {
                    if (entry != null && !entry.isBlank()) {
                        texts.add(entry);
                    }
                });
            }
        });
        return texts;
    }

    /** Percentage of the drive's required skills present in the student's skills (null if none required). */
    private Integer skillCoverage(List<String> requiredSkills, List<String> strengths) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return null;
        }
        long matched = requiredSkills.stream().filter(skill -> containsIgnoreCase(strengths, skill)).count();
        return (int) Math.round(100.0 * matched / requiredSkills.size());
    }

    private List<String> presentSkills(List<String> requiredSkills, List<String> strengths) {
        if (requiredSkills == null) {
            return List.of();
        }
        return requiredSkills.stream().filter(skill -> containsIgnoreCase(strengths, skill)).toList();
    }

    private List<String> missingSkills(List<String> requiredSkills, List<String> strengths) {
        if (requiredSkills == null) {
            return List.of();
        }
        return requiredSkills.stream().filter(skill -> !containsIgnoreCase(strengths, skill)).toList();
    }

    private boolean containsIgnoreCase(List<String> haystack, String needle) {
        if (haystack == null) {
            return false;
        }
        String target = needle.toLowerCase(Locale.ROOT);
        return haystack.stream().anyMatch(s -> s != null && s.toLowerCase(Locale.ROOT).equals(target));
    }

    private String recommendationFor(String status, int overallFit) {
        return switch (status == null ? "" : status) {
            case "NOT_ELIGIBLE" -> "NOT ELIGIBLE";
            case "INSUFFICIENT_DATA" -> "INSUFFICIENT DATA";
            case "ELIGIBLE_LOW_FIT" -> "CONSIDER";
            case "ELIGIBLE_RANKED" -> overallFit >= 80 ? "STRONG MATCH" : "CONSIDER";
            default -> "REVIEW";
        };
    }

    private static Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Percentage of the drive's required skills backed by at least one of the student's
     * certifications (name containment, case-insensitive). Null when there are no required skills.
     */
    private Integer certificationRelevance(List<String> requiredSkills, List<String> certNames) {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return null;
        }
        long backed = requiredSkills.stream()
            .filter(skill -> certNames.stream().anyMatch(
                cert -> cert != null && !cert.isBlank()
                    && containsIgnoreCase(skill, cert)))
            .count();
        return (int) Math.round(100.0 * backed / requiredSkills.size());
    }

    /** True when either string contains the other, compared case-insensitively. */
    private static boolean containsIgnoreCase(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        String la = a.toLowerCase(Locale.ROOT);
        String lb = b.toLowerCase(Locale.ROOT);
        return la.contains(lb) || lb.contains(la);
    }

    /**
     * Pass rate (0-100) over the student's completed interviews — result SELECTED or REJECTED —
     * excluding the drive being ranked, so a drive's own outcomes never feed back into its own
     * ranking. Null when nothing is completed: missing evidence, never imputed as zero.
     */
    private Integer historicalInterviewPassRate(String studentId, String driveId) {
        List<Interview> completed = interviewRepository.findByStudentId(studentId).stream()
            .filter(entry -> !driveId.equals(entry.getDriveId()))
            .filter(entry -> entry.getResult() == InterviewResult.SELECTED
                || entry.getResult() == InterviewResult.REJECTED)
            .toList();
        if (completed.isEmpty()) {
            return null;
        }
        long passed = completed.stream()
            .filter(entry -> entry.getResult() == InterviewResult.SELECTED)
            .count();
        return (int) Math.round(100.0 * passed / completed.size());
    }

    /** Per-candidate computed features kept for building the display response after ranking. */
    private record Prep(
        boolean eligible,
        Integer coverage,
        Integer interview,
        Integer projects,
        Integer academics,
        Integer readiness,
        Integer certificationRelevance,
        Integer historicalInterview,
        List<String> present,
        List<String> missing,
        BigDecimal cgpa
    ) {
    }
}
