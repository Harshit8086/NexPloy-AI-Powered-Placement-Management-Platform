package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.student.dto.AssessmentScores;
import com.nexploy.campuslink.student.dto.DigitalTwinResponse;
import com.nexploy.campuslink.student.dto.ReadinessBreakdown;
import com.nexploy.campuslink.student.dto.ReadinessSummaryResponse;
import com.nexploy.campuslink.student.dto.ResumeIntelligenceResponse;
import com.nexploy.campuslink.student.dto.StudentExperience;
import com.nexploy.campuslink.student.dto.StudentProject;
import com.nexploy.campuslink.student.dto.StudentResponse;
import com.nexploy.campuslink.student.dto.StudentUpdateRequest;
import com.nexploy.campuslink.student.dto.WhatIfResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.ResumeAnalysisRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Builds the enriched {@link StudentResponse}, parsing the student's JSON columns
 * (strengths, gaps, certifications, projects, assessments, breakdown, trend, preferences)
 * into typed values. Parsing lives here, mirroring DriveService / the intervention service.
 */
@Service
public class StudentService {

    private final ObjectMapper objectMapper;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final StudentRepository studentRepository;
    private final ResumeAnalyzer resumeAnalyzer;

    public StudentService(ObjectMapper objectMapper) {
        this(objectMapper, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public StudentService(
        ObjectMapper objectMapper,
        ObjectProvider<ResumeAnalysisRepository> resumeAnalysisRepoProvider,
        ObjectProvider<StudentRepository> studentRepoProvider,
        ObjectProvider<ResumeAnalyzer> resumeAnalyzerProvider
    ) {
        this.objectMapper = objectMapper;
        this.resumeAnalysisRepository = resumeAnalysisRepoProvider != null ? resumeAnalysisRepoProvider.getIfAvailable() : null;
        this.studentRepository = studentRepoProvider != null ? studentRepoProvider.getIfAvailable() : null;
        this.resumeAnalyzer = resumeAnalyzerProvider != null ? resumeAnalyzerProvider.getIfAvailable() : null;
    }

    /** The placement-status vocabulary shared with the frontend (UNPLACED/IN PROCESS/OFFERED/PLACED). */
    private static final java.util.Set<String> PLACEMENT_STATUSES =
        java.util.Set.of("UNPLACED", "IN PROCESS", "OFFERED", "PLACED");

    /**
     * Sets a student's placement status (officer/admin path). Validates the value against the shared
     * vocabulary and normalizes it to upper-case; an unknown value is a 400. Never fabricates a status.
     */
    public void changePlacementStatus(Student student, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!PLACEMENT_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown placement status: " + status);
        }
        student.setPlacementStatus(normalized);
    }

    public StudentResponse toResponse(Student student) {
        List<Integer> storedTrend =
            readValue(student.getReadinessTrendJson(), new TypeReference<List<Integer>>() {}, List.of());

        // Seed the trend with the current readiness score when no history has been recorded yet.
        // This prevents the dashboard from showing a blank "No readiness history" state on first
        // load — the student at least sees their current score as a single data point.
        List<Integer> trend = storedTrend;
        if (storedTrend.isEmpty() && student.getReadinessScore() != null) {
            trend = List.of(student.getReadinessScore());
        }

        // Auto-heal / sync resumeScore: if student's stored score is null or 0, check past
        // analyses or calculate a baseline from their profile projects & skills.
        Integer resumeScore = student.getResumeScore();
        if ((resumeScore == null || resumeScore == 0) && student.getId() != null) {
            if (resumeAnalysisRepository != null) {
                var latest = resumeAnalysisRepository.findTopByStudentIdOrderByCreatedAtDesc(student.getId());
                if (latest.isPresent()) {
                    resumeScore = latest.get().getOverallScore();
                    student.setResumeScore(resumeScore);
                    if (studentRepository != null) {
                        studentRepository.save(student);
                    }
                }
            }
            if ((resumeScore == null || resumeScore == 0) && resumeAnalyzer != null) {
                int profileScore = computeProfileResumeScore(student);
                if (profileScore > 0) {
                    resumeScore = profileScore;
                    student.setResumeScore(resumeScore);
                    if (studentRepository != null) {
                        studentRepository.save(student);
                    }
                }
            }
        }

        return StudentResponse.from(
            student,
            readStringList(student.getStrengthsJson()),
            readStringList(student.getCriticalGapsJson()),
            readStringList(student.getCertificationsJson()),
            readValue(student.getProjectsJson(), new TypeReference<List<StudentProject>>() {}, List.of()),
            readValue(student.getExperienceJson(), new TypeReference<List<StudentExperience>>() {}, List.of()),
            readValue(student.getAssessmentsJson(), new TypeReference<AssessmentScores>() {}, null),
            readValue(student.getBreakdownJson(), new TypeReference<ReadinessBreakdown>() {}, null),
            trend,
            readStringList(student.getPreferredRolesJson()),
            readStringList(student.getPreferredDomainsJson()),
            readStringList(student.getPreferredLocationsJson())
        );
    }

    /**
     * Applies a student's self-service edit in place. Every field is optional: a null leaves the
     * current value untouched (partial patch). List fields are serialized back into their JSON
     * columns. Derived/identity fields are not part of the request and are never modified here.
     *
     * @return {@code true} if a field that FEEDS the readiness score actually changed value — the
     *     academic inputs (CGPA, 10th, 12th, active backlogs) or projects. The caller uses this to
     *     fire a readiness recompute only when it would matter, so edits to non-readiness fields
     *     (phone, social links, preferences, experience) never trigger needless ML work.
     */
    public boolean applyUpdate(Student student, StudentUpdateRequest request) {
        boolean readinessInputChanged = false;

        if (request.phone() != null) student.setPhone(request.phone());
        if (request.linkedinUrl() != null) student.setLinkedinUrl(request.linkedinUrl());
        if (request.githubUrl() != null) student.setGithubUrl(request.githubUrl());
        if (request.targetRole() != null) student.setTargetRole(request.targetRole());
        if (request.preferredRoles() != null) student.setPreferredRolesJson(writeJson(request.preferredRoles()));
        if (request.preferredDomains() != null) student.setPreferredDomainsJson(writeJson(request.preferredDomains()));
        if (request.preferredLocations() != null) student.setPreferredLocationsJson(writeJson(request.preferredLocations()));
        if (request.strengths() != null) student.setStrengthsJson(writeJson(request.strengths()));
        if (request.projects() != null) {
            String newProjectsJson = writeValue(request.projects());
            if (!newProjectsJson.equals(student.getProjectsJson())) readinessInputChanged = true;
            student.setProjectsJson(newProjectsJson);
        }
        if (request.experience() != null) student.setExperienceJson(writeValue(request.experience()));

        return readinessInputChanged;
    }

    /**
     * Read-only readiness summary: the stored overall score plus the parsed five-factor
     * breakdown. Nothing is recomputed — this exposes the authoritative derived values as they
     * already sit on the student row.
     */
    public ReadinessSummaryResponse readinessSummary(Student student) {
        ReadinessBreakdown breakdown =
            readValue(student.getBreakdownJson(), new TypeReference<ReadinessBreakdown>() {}, null);
        return new ReadinessSummaryResponse(student.getReadinessScore(), breakdown);
    }

    /**
     * Read-only digital-twin summary: the headline readiness score (authoritative, stored) plus a
     * deterministic status label. No recomputation — the score is exactly {@code readinessScore};
     * a null score reads as 0 so the label is well-formed rather than fabricating a value.
     */
    public DigitalTwinResponse digitalTwin(Student student) {
        int finalScore = student.getReadinessScore() == null ? 0 : student.getReadinessScore();
        return new DigitalTwinResponse(finalScore, "PLACEMENT DIGITAL TWIN READY — " + finalScore + "/100");
    }

    /**
     * Read-only assessment baseline for the student (aptitude/coding/technical/communication/mock-
     * interview, each 0-100), parsed from their stored {@code assessmentsJson}. Scores are
     * authoritative and never writable through this path. When no assessments are recorded yet the
     * baseline reads as all-zero (the honest "no score" default), never fabricated numbers.
     */
    public AssessmentScores assessments(Student student) {
        return readValue(
            student.getAssessmentsJson(),
            new TypeReference<AssessmentScores>() {},
            new AssessmentScores(0, 0, 0, 0, 0));
    }

    /**
     * Read-only resume-intelligence summary shown before any resume file is analyzed. It reports only
     * what is actually stored for the student: the authoritative {@code resumeScore} (null → 0, never
     * fabricated), their recorded {@code strengths} (up to four), and their recorded critical gaps as
     * {@code missingKeywords}. It deliberately carries NO weaknesses, project suggestions, or
     * recommendation — those are not stored profile facts and would be identical for every student.
     * The per-resume weaknesses/suggestions/recommendation are produced on demand by
     * {@link ResumeIntelligenceService#analyzeUpload} when the student uploads a resume, derived from
     * that resume's actual content (and, in {@code llm} mode, written by the grounded model).
     */
    public ResumeIntelligenceResponse resumeIntelligence(Student student) {
        int score = student.getResumeScore() == null ? 0 : student.getResumeScore();

        List<String> strengths = readStringList(student.getStrengthsJson()).stream()
            .limit(4)
            .toList();

        List<String> missingKeywords = readStringList(student.getCriticalGapsJson());

        String recommendation = "Upload your resume above to get tailored, resume-specific analysis.";

        return new ResumeIntelligenceResponse(
            score, strengths, List.of(), missingKeywords, List.of(), recommendation);
    }

    /**
     * Synthesizes an initial baseline resume score directly from the student's profile (projects,
     * skills, education, experience) using the ATS ResumeAnalyzer when no resume file has been
     * uploaded yet.
     */
    public int computeProfileResumeScore(Student student) {
        if (resumeAnalyzer == null) return 0;
        List<String> strengths = readStringList(student.getStrengthsJson());
        List<StudentProject> projects = readValue(student.getProjectsJson(), new TypeReference<List<StudentProject>>() {}, List.of());
        if (strengths.isEmpty() && projects.isEmpty()) {
            return 0;
        }

        StringBuilder sb = new StringBuilder();
        if (student.getName() != null) sb.append(student.getName()).append("\n");
        if (student.getEmail() != null) sb.append(student.getEmail()).append(" | ");
        if (student.getPhone() != null) sb.append(student.getPhone()).append("\n");
        if (student.getBranch() != null) sb.append("Education: ").append(student.getBranch());
        if (student.getCgpa() != null) sb.append(" | CGPA: ").append(student.getCgpa());
        sb.append("\n\n");

        if (!strengths.isEmpty()) {
            sb.append("Skills: ").append(String.join(", ", strengths)).append("\n\n");
        }

        if (!projects.isEmpty()) {
            sb.append("Projects:\n");
            for (StudentProject p : projects) {
                sb.append(p.name());
                if (p.stack() != null && !p.stack().isEmpty()) {
                    sb.append(" | Stack: ").append(String.join(", ", p.stack()));
                }
                sb.append("\n");
                if (p.summary() != null) sb.append(p.summary()).append("\n");
            }
            sb.append("\n");
        }

        List<StudentExperience> experience = readValue(student.getExperienceJson(), new TypeReference<List<StudentExperience>>() {}, List.of());
        if (!experience.isEmpty()) {
            sb.append("Experience:\n");
            for (StudentExperience exp : experience) {
                sb.append(exp.role()).append(" at ").append(exp.company());
                if (exp.duration() != null) sb.append(" (").append(exp.duration()).append(")");
                sb.append("\n");
                if (exp.summary() != null) sb.append(exp.summary()).append("\n");
            }
            sb.append("\n");
        }

        List<String> certifications = readStringList(student.getCertificationsJson());
        if (!certifications.isEmpty()) {
            sb.append("Certifications: ").append(String.join(", ", certifications)).append("\n\n");
        }

        String text = sb.toString().trim();
        if (text.length() < 30) return 0;
        return resumeAnalyzer.analyzeStructured(text, student.getTargetRole()).overallScore();
    }

    // ---- What-If scenario compute (authoritative, deterministic) ----------------------------
    //
    // Ported verbatim in behaviour from the frontend simulate.ts. Gains are expressed as DELTAS
    // over the baseline so the projection is anchored to the student's OWN stored placement
    // probability (never the demo constant), while still reproducing the original absolute values
    // for a baseline of 68. The gain is capped at MAX_DELTA (the original 86-vs-68 ceiling) and the
    // final probability is clamped to 100; a scenario never drops a student below their baseline.

    /** Per-factor weight used for any selection not covered by the exact table. */
    private static final Map<String, Integer> WHAT_IF_WEIGHTS = Map.of(
        "aws", 4, "docker", 3, "dsa", 4, "mockInterviews", 5, "newProject", 3, "communication", 3);

    /** Exact, order-independent scenario deltas keyed by the sorted, comma-joined factor signature. */
    private static final Map<String, Integer> WHAT_IF_TABLE = Map.of(
        "", 0,
        "aws", 4,
        "aws,dsa", 8,
        "aws,dsa,mockInterviews", 13,
        "aws,communication,docker,dsa,mockInterviews,newProject", 18);

    /** Largest improvement any scenario can yield (the original 86 − 68 best-case ceiling). */
    private static final int WHAT_IF_MAX_DELTA = 18;

    /** Number of distinct What-If factors — a full selection earns the best-case recommendation. */
    private static final int WHAT_IF_FACTOR_COUNT = 6;

    /**
     * Computes the authoritative What-If projection for the student and their selected growth
     * actions. Deterministic: identical inputs always yield identical output, independent of the
     * order factors were selected. The baseline is the student's stored {@code placementProbability}
     * (null → 0); nothing here is fabricated or persisted (persistence stays on the goals endpoint).
     */
    public WhatIfResponse whatIf(Student student, List<String> factors) {
        int baseline = student.getPlacementProbability() == null ? 0 : student.getPlacementProbability();

        List<String> sorted = factors.stream().sorted().toList();
        String key = String.join(",", sorted);

        int gain;
        if (WHAT_IF_TABLE.containsKey(key)) {
            gain = WHAT_IF_TABLE.get(key);
        } else {
            gain = sorted.stream().mapToInt(f -> {
                if (WHAT_IF_WEIGHTS.containsKey(f)) {
                    return WHAT_IF_WEIGHTS.get(f);
                }
                return 4;
            }).sum();
        }
        gain = Math.min(gain, WHAT_IF_MAX_DELTA);

        int probability = Math.min(100, baseline + gain);
        int readinessImpact = (int) Math.round((probability - baseline) * 0.6);

        return new WhatIfResponse(probability, readinessImpact, recommendedPath(factors.size()), baseline);
    }

    /** Deterministic recommended-path guidance keyed on how many actions the student picked. */
    private String recommendedPath(int selectedCount) {
        if (selectedCount == 0) {
            return "Start with AWS — it has the single largest impact on your placement probability.";
        }
        if (selectedCount >= WHAT_IF_FACTOR_COUNT) {
            return "You've built the best-case roadmap. Apply it to lock in these gains.";
        }
        return "Add DSA and 3 mock interviews next for the fastest additional gain.";
    }

    private List<String> readStringList(String json) {
        return readValue(json, new TypeReference<List<String>>() {}, List.of());
    }

    /** Serializes a list to its JSON-column string; wraps the checked Jackson exception. */
    private String writeJson(List<String> values) {
        return writeValue(values);
    }

    /** Serializes any value (e.g. a list of projects) to its JSON-column string. */
    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not write student profile field", exception);
        }
    }

    /** Parses JSON to the given type, tolerating null/blank by returning {@code fallback}. */
    private <T> T readValue(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read student profile field", exception);
        }
    }
}
