package com.nexploy.campuslink.student.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.assessment.entity.AssessmentRecord;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.repository.AssessmentRecordRepository;
import com.nexploy.campuslink.readiness.dto.ReadinessScoreRequestBody;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.entity.StudentProjectEntry;
import com.nexploy.campuslink.student.repository.StudentProjectEntryRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Derives a null-aware {@link ReadinessScoreRequestBody} for a student from the platform's
 * EXISTING data sources — never a caller-supplied body, and never a duplicate table:
 *
 * <ul>
 *   <li><b>Aptitude / Communication / Mock&nbsp;Interview</b> — the latest
 *       {@link AssessmentRecord} per type ({@code assessment_records}). The record's EXISTENCE is
 *       what matters: a type the student has never been assessed on is sent as {@code null} (so the
 *       ML service excludes it and renormalises), NOT read from the {@code students.assessment_*}
 *       snapshot columns, which {@code StudentAssessmentWriter} zero-fills for absent types and so
 *       cannot distinguish "no test" from "scored 0". Mock&nbsp;Interview maps to the ML
 *       {@code interview} dimension.</li>
 *   <li><b>Academic</b> — a weighted blend of {@code cgpa} (×10), {@code tenthPercentage} and
 *       {@code twelfthPercentage} over whichever sub-signals are present, minus a per-active-backlog
 *       penalty; {@code null} when all three academic sub-signals are absent.</li>
 *   <li><b>Projects</b> — a documented qualitative heuristic over {@code student_projects}
 *       (count, summary presence, tech-stack breadth); {@code null} when the student has no
 *       projects.</li>
 * </ul>
 *
 * Weights and heuristic constants are configuration (see {@code nexploy.readiness.*}); the ML
 * service owns the dimension weights and the renormalisation.
 */
@Service
@RequiredArgsConstructor
public class ReadinessInputAssembler {

    private final AssessmentRecordRepository assessmentRecordRepository;
    private final StudentProjectEntryRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Value("${nexploy.readiness.academic.cgpa-weight:0.6}")
    private double cgpaWeight;

    @Value("${nexploy.readiness.academic.tenth-weight:0.2}")
    private double tenthWeight;

    @Value("${nexploy.readiness.academic.twelfth-weight:0.2}")
    private double twelfthWeight;

    @Value("${nexploy.readiness.academic.backlog-penalty:3.0}")
    private double backlogPenalty;

    @Value("${nexploy.readiness.projects.base-per-project:20}")
    private int projectBase;

    @Value("${nexploy.readiness.projects.summary-bonus:10}")
    private int summaryBonus;

    @Value("${nexploy.readiness.projects.stack-point:4}")
    private int stackPoint;

    @Value("${nexploy.readiness.projects.max-stack:5}")
    private int maxStack;

    /** Builds the derived, null-aware readiness inputs for the student from existing data only. */
    public ReadinessScoreRequestBody assemble(Student student) {
        Map<AssessmentType, Integer> latest = latestPerType(student.getId());
        return new ReadinessScoreRequestBody(
            academicScore(student),
            toDouble(latest.get(AssessmentType.APTITUDE)),
            toDouble(latest.get(AssessmentType.COMMUNICATION)),
            projectScore(student.getId()),
            toDouble(latest.get(AssessmentType.MOCK_INTERVIEW)));
    }

    /**
     * Latest normalized score per assessment type the student ACTUALLY has a record for. Absent
     * types are simply not keyed, so the caller sends {@code null} (excluded + renormalised) rather
     * than a fabricated zero. Latest = newest {@code assessmentDate}, ties broken by {@code updatedAt}.
     */
    private Map<AssessmentType, Integer> latestPerType(String studentId) {
        Map<AssessmentType, AssessmentRecord> latest = new EnumMap<>(AssessmentType.class);
        for (AssessmentRecord record : assessmentRecordRepository.findByStudentId(studentId)) {
            latest.merge(record.getAssessmentType(), record,
                (current, candidate) -> isNewer(candidate, current) ? candidate : current);
        }
        Map<AssessmentType, Integer> scores = new EnumMap<>(AssessmentType.class);
        latest.forEach((type, record) -> scores.put(type, record.getNormalizedScore()));
        return scores;
    }

    /** Later assessment date wins; a tie is broken by the more recently updated row. */
    private boolean isNewer(AssessmentRecord candidate, AssessmentRecord current) {
        int byDate = candidate.getAssessmentDate().compareTo(current.getAssessmentDate());
        if (byDate != 0) {
            return byDate > 0;
        }
        OffsetDateTime a = candidate.getUpdatedAt();
        OffsetDateTime b = current.getUpdatedAt();
        if (a == null || b == null) {
            return a != null;
        }
        return a.isAfter(b);
    }

    /**
     * Academic = renormalised weighted blend of the present sub-signals ({@code cgpa}×10, tenth,
     * twelfth) minus {@code backlogPenalty} per active backlog, clamped to 0..100. Returns
     * {@code null} when no academic sub-signal is present, so academic is excluded rather than zeroed.
     */
    private Double academicScore(Student student) {
        double weightSum = 0.0;
        double weighted = 0.0;

        BigDecimal cgpa = student.getCgpa();
        if (cgpa != null) {
            weighted += cgpaWeight * clamp(cgpa.doubleValue() * 10.0);
            weightSum += cgpaWeight;
        }
        BigDecimal tenth = student.getTenthPercentage();
        if (tenth != null) {
            weighted += tenthWeight * clamp(tenth.doubleValue());
            weightSum += tenthWeight;
        }
        BigDecimal twelfth = student.getTwelfthPercentage();
        if (twelfth != null) {
            weighted += twelfthWeight * clamp(twelfth.doubleValue());
            weightSum += twelfthWeight;
        }

        if (weightSum == 0.0) {
            return null;
        }

        double base = weighted / weightSum;
        int backlogs = student.getBacklogs() == null ? 0 : Math.max(0, student.getBacklogs());
        return clamp(base - backlogPenalty * backlogs);
    }

    /**
     * Projects = summed per-project heuristic, capped at 100; {@code null} when the student has no
     * projects. Per project: {@code base + (summary present ? bonus : 0) + stackPoint·min(stack, max)}.
     * A documented qualitative proxy — {@code student_projects} carries no numeric score.
     */
    private Double projectScore(String studentId) {
        List<StudentProjectEntry> projects = projectRepository.findByStudentIdOrderBySortOrder(studentId);
        if (projects.isEmpty()) {
            return null;
        }
        double total = 0.0;
        for (StudentProjectEntry project : projects) {
            double points = projectBase;
            if (project.getSummary() != null && !project.getSummary().isBlank()) {
                points += summaryBonus;
            }
            points += stackPoint * Math.min(stackSize(project.getStackJson()), maxStack);
            total += points;
        }
        return Math.min(100.0, total);
    }

    /** Size of the project's JSON stack array; 0 for null/blank/unparseable (never throws). */
    private int stackSize(String stackJson) {
        if (stackJson == null || stackJson.isBlank()) {
            return 0;
        }
        try {
            List<String> stack = objectMapper.readValue(stackJson, new TypeReference<List<String>>() {});
            return stack == null ? 0 : stack.size();
        } catch (JsonProcessingException exception) {
            return 0;
        }
    }

    private static Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
