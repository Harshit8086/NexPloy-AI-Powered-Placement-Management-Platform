package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.ai.service.PromptInjectionGuard;
import com.nexploy.campuslink.ai.service.ResumeTextExtractor;
import com.nexploy.campuslink.student.dto.ResumeAnalysisDetail;
import com.nexploy.campuslink.student.dto.ResumeAnalysisResponse;
import com.nexploy.campuslink.student.dto.ResumeAnalysisSummary;
import com.nexploy.campuslink.student.dto.ResumeIntelligenceResponse;
import com.nexploy.campuslink.student.dto.ResumeQualityAnalysis;
import com.nexploy.campuslink.student.entity.ResumeAnalysis;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.exception.ResumeAnalysisNotFoundException;
import com.nexploy.campuslink.student.repository.ResumeAnalysisRepository;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.service.ResumeInsightWriter.ResumeInsight;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resume-intelligence analysis over an uploaded resume file. It parses the file to text, screens it
 * for prompt injection, runs the deterministic {@link ResumeAnalyzer}, then — when
 * {@code nexploy.ai.provider=llm} — overlays richer, resume-specific prose from the optional
 * {@link ResumeInsightWriter}. The raw resume file and its full extracted text are never stored; the
 * structured <em>result</em> of a run is persisted (Phase B) to power the Analysis History section,
 * so a student can revisit prior runs and see progress over time.
 *
 * <p>Authority split mirrors the skill-gap seam: the {@code score}, {@code strengths} and
 * {@code missingKeywords} are the analyzer's and are never delegated to the model; only the three
 * free-text fields (weaknesses / projectSuggestions / recommendation) may be replaced, and only when
 * the writer returns valid, non-empty content.
 */
@Service
@RequiredArgsConstructor
public class ResumeIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ResumeIntelligenceService.class);

    // Guards against a runaway or malformed LLM field: anything longer is treated as invalid and the
    // deterministic text is kept.
    private static final int MAX_TEXT_LENGTH = 600;

    private final ResumeTextExtractor resumeTextExtractor;
    private final PromptInjectionGuard promptInjectionGuard;
    private final ResumeAnalyzer resumeAnalyzer;
    private final ResumeQualityAnalyzer resumeQualityAnalyzer;
    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper;
    // Optional LLM enhancer. Present only when nexploy.ai.provider=llm; null in the default
    // rule-based mode, in which case the analyzer's deterministic text is returned unchanged.
    private final ObjectProvider<ResumeInsightWriter> insightWriterProvider;

    /**
     * Analyzes an uploaded resume for the given student. The file is parsed server-side, screened,
     * and analyzed; nothing is stored.
     */
    public ResumeIntelligenceResponse analyzeUpload(MultipartFile file, Student student) {
        String text = resumeTextExtractor.extract(file);
        promptInjectionGuard.verify(text);

        ResumeIntelligenceResponse baseline = resumeAnalyzer.analyze(text, student.getTargetRole());
        student.setResumeScore(baseline.score());
        studentRepository.save(student);
        return withInsight(text, student.getTargetRole(), baseline);
    }

    /**
     * Deep structured analysis of an uploaded resume. Runs the deterministic structured analyzer,
     * then — in {@code llm} mode — overlays only the grounded LLM {@code recommendation}. The score,
     * metrics, skill-match, extracted items and structured weaknesses stay the analyzer's; any writer
     * failure falls back to the deterministic recommendation. The result (not the raw resume) is
     * persisted as a history record for the student.
     */
    public ResumeAnalysisResponse analyzeUploadStructured(MultipartFile file, Student student) {
        String text = resumeTextExtractor.extract(file);
        promptInjectionGuard.verify(text);

        ResumeAnalysisResponse baseline = resumeAnalyzer.analyzeStructured(text, student.getTargetRole());
        String recommendation = insightRecommendation(text, student.getTargetRole(), baseline);
        // §10 Resume Quality — deterministic, evidence-grounded, overlaid onto the analyzer baseline
        // (the stateless analyzer itself leaves this null; this service is the only place it is set).
        ResumeQualityAnalysis quality = resumeQualityAnalyzer.analyze(text, student.getTargetRole(), baseline);
        ResumeAnalysisResponse response = new ResumeAnalysisResponse(
            baseline.overallScore(), baseline.metrics(), baseline.strengths(), baseline.weaknesses(),
            baseline.missingKeywords(), baseline.projectSuggestions(), baseline.skillMatch(),
            baseline.extracted(), recommendation, baseline.actionPlan(), quality);

        persist(student, file, response);
        return response;
    }

    /**
     * The student's resume Analysis History, newest first. Each entry carries only the run's id,
     * resume filename, headline score and timestamp; the full analysis is fetched via {@link #history}
     * detail on demand.
     */
    public List<ResumeAnalysisSummary> history(Student student) {
        return resumeAnalysisRepository.findByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
            .map(r -> new ResumeAnalysisSummary(r.getId(), r.getResumeName(), r.getOverallScore(), r.getCreatedAt()))
            .toList();
    }

    /**
     * One past analysis, restored in full for the given student. Scoped to the owner, so a student can
     * only ever open their own run; a missing or foreign id yields a 404.
     */
    public ResumeAnalysisDetail historyDetail(Student student, String id) {
        ResumeAnalysis record = resumeAnalysisRepository.findByIdAndStudentId(id, student.getId())
            .orElseThrow(() -> new ResumeAnalysisNotFoundException(id));
        ResumeAnalysisResponse analysis;
        try {
            analysis = objectMapper.readValue(record.getAnalysisJson(), ResumeAnalysisResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // A stored payload we can no longer parse (e.g. after a schema change) is treated as gone
            // rather than surfaced as a 500 — history is descriptive, not authoritative.
            log.warn("Stored resume analysis {} could not be parsed: {}", id, e.toString());
            throw new ResumeAnalysisNotFoundException(id);
        }
        return new ResumeAnalysisDetail(record.getId(), record.getResumeName(), record.getCreatedAt(), analysis);
    }

    /** Stores the analysis result (never the raw resume) as one history row for the student. */
    private void persist(Student student, MultipartFile file, ResumeAnalysisResponse response) {
        String analysisJson;
        try {
            analysisJson = objectMapper.writeValueAsString(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Serialization should never fail for our own record; if it somehow does, skip persistence
            // rather than fail the analysis the student is waiting on.
            log.warn("Could not serialize resume analysis for history; skipping persistence. cause={}", e.toString());
            return;
        }
        ResumeAnalysis record = ResumeAnalysis.builder()
            .id("resume_analysis_" + UUID.randomUUID())
            .studentId(student.getId())
            .campusId(student.getCampusId())
            .resumeName(file.getOriginalFilename())
            .overallScore(response.overallScore())
            .analysisJson(analysisJson)
            .createdAt(OffsetDateTime.now())
            .build();
        resumeAnalysisRepository.save(record);

        student.setResumeScore(response.overallScore());
        studentRepository.save(student);
    }

    /** Returns the LLM-enriched recommendation when available and valid, else the deterministic one. */
    private String insightRecommendation(String text, String targetRole, ResumeAnalysisResponse baseline) {
        ResumeInsightWriter writer = insightWriterProvider.getIfAvailable();
        if (writer == null) {
            return baseline.recommendation();
        }
        List<String> gaps = baseline.weaknesses().stream().map(ResumeAnalysisResponse.Weakness::gap).toList();
        try {
            ResumeInsight insight = writer.write(text, targetRole, gaps, baseline.missingKeywords());
            if (insight != null && validText(insight.recommendation())) {
                return insight.recommendation().trim();
            }
        } catch (RuntimeException e) {
            log.warn("Resume insight writer failed; using deterministic recommendation. cause={}", e.toString());
        }
        return baseline.recommendation();
    }

    /**
     * Overlays LLM-written prose onto the deterministic baseline when the writer is wired and returns
     * valid text. The score, strengths and missing keywords are always the analyzer's. Any failure
     * (writer absent, exception, or invalid fields) falls back to the deterministic text.
     */
    private ResumeIntelligenceResponse withInsight(String text, String targetRole, ResumeIntelligenceResponse baseline) {
        ResumeInsightWriter writer = insightWriterProvider.getIfAvailable();
        if (writer == null) {
            return baseline;
        }
        ResumeInsight insight;
        try {
            insight = writer.write(text, targetRole, baseline.weaknesses(), baseline.missingKeywords());
        } catch (RuntimeException e) {
            // LLM disabled mid-flight, API error, timeout, quota, prompt-injection guard, etc.
            log.warn("Resume insight writer failed; using deterministic text. cause={}", e.toString());
            return baseline;
        }
        if (insight == null) {
            return baseline;
        }
        List<String> weaknesses = validList(insight.weaknesses()) ? trim(insight.weaknesses()) : baseline.weaknesses();
        List<String> suggestions = validList(insight.projectSuggestions())
            ? trim(insight.projectSuggestions()) : baseline.projectSuggestions();
        String recommendation = validText(insight.recommendation())
            ? insight.recommendation().trim() : baseline.recommendation();
        return new ResumeIntelligenceResponse(
            baseline.score(),
            baseline.strengths(),
            weaknesses,
            baseline.missingKeywords(),
            suggestions,
            recommendation);
    }

    private boolean validList(List<String> items) {
        return items != null && !items.isEmpty() && items.stream().allMatch(this::validText);
    }

    private boolean validText(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_TEXT_LENGTH;
    }

    private List<String> trim(List<String> items) {
        return items.stream().map(String::trim).toList();
    }
}
