package com.nexploy.campuslink.assessment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexploy.campuslink.assessment.entity.AssessmentRecord;
import com.nexploy.campuslink.assessment.entity.AssessmentType;
import com.nexploy.campuslink.assessment.repository.AssessmentRecordRepository;
import com.nexploy.campuslink.student.dto.AssessmentScores;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one student's imported assessment rows and rebuilds their current snapshot, in a
 * single transaction. A separate bean (not a self-invoked method) so each student's import runs
 * in its own transaction — one student's failure rolls back only that student, never the batch.
 */
@Service
@RequiredArgsConstructor
public class StudentAssessmentWriter {

    private final AssessmentRecordRepository recordRepository;
    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper;

    /** Created/updated tally for one student's import. */
    public record ApplyResult(int created, int updated) {
    }

    /**
     * Upserts each row on the business key {@code (student, type, name, date)}, then recomputes the
     * five-field snapshot (latest test per type, preserving dimensions with no records) and saves it.
     */
    @Transactional
    public ApplyResult applyForStudent(Student student, List<PreparedAssessmentRow> rows) {
        int created = 0;
        int updated = 0;
        OffsetDateTime now = OffsetDateTime.now();

        for (PreparedAssessmentRow row : rows) {
            Optional<AssessmentRecord> existing = recordRepository
                .findByStudentIdAndAssessmentTypeAndAssessmentNameAndAssessmentDate(
                    student.getId(), row.type(), row.name(), row.date());
            if (existing.isPresent()) {
                AssessmentRecord record = existing.get();
                record.setCampusId(student.getCampusId());
                record.setRawScore(row.rawScore());
                record.setMaxScore(row.maxScore());
                record.setNormalizedScore(row.normalizedScore());
                record.setSource(row.source());
                record.setUpdatedAt(now);
                recordRepository.save(record);
                updated++;
            } else {
                recordRepository.save(AssessmentRecord.builder()
                    .id("asr_" + UUID.randomUUID())
                    .studentId(student.getId())
                    .campusId(student.getCampusId())
                    .assessmentType(row.type())
                    .assessmentName(row.name())
                    .rawScore(row.rawScore())
                    .maxScore(row.maxScore())
                    .normalizedScore(row.normalizedScore())
                    .assessmentDate(row.date())
                    .source(row.source())
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
                created++;
            }
        }

        rebuildSnapshot(student);
        studentRepository.save(student);
        return new ApplyResult(created, updated);
    }

    /**
     * Recomputes the current five-field snapshot from all of the student's records. Every dimension
     * starts at 0; for each type that has at least one record, the LATEST test by
     * {@code assessmentDate} (tie-broken by {@code updatedAt}) replaces that 0 with its normalized
     * score. Because {@code assessment_records} is append-only, a dimension scored by any earlier
     * import is still present in the history and is therefore preserved; only dimensions the student
     * has never been assessed on remain 0. Writes both {@code assessmentsJson} (authoritative read
     * source) and the mirrored {@code assessment_*} columns from the same computed values.
     */
    private void rebuildSnapshot(Student student) {
        Map<AssessmentType, AssessmentRecord> latest = new EnumMap<>(AssessmentType.class);
        for (AssessmentRecord record : recordRepository.findByStudentId(student.getId())) {
            latest.merge(record.getAssessmentType(), record,
                (current, candidate) -> isNewer(candidate, current) ? candidate : current);
        }

        int aptitude = pick(latest, AssessmentType.APTITUDE);
        int coding = pick(latest, AssessmentType.CODING);
        int technical = pick(latest, AssessmentType.TECHNICAL);
        int communication = pick(latest, AssessmentType.COMMUNICATION);
        int mockInterview = pick(latest, AssessmentType.MOCK_INTERVIEW);

        student.setAssessmentsJson(
            writeJson(new AssessmentScores(aptitude, coding, technical, communication, mockInterview)));
        student.setAssessmentAptitude(aptitude);
        student.setAssessmentCoding(coding);
        student.setAssessmentTechnical(technical);
        student.setAssessmentCommunication(communication);
        student.setAssessmentMockInterview(mockInterview);
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

    /** Latest normalized score for the type, or 0 when the student has no record of that type. */
    private int pick(Map<AssessmentType, AssessmentRecord> latest, AssessmentType type) {
        AssessmentRecord record = latest.get(type);
        return record != null ? record.getNormalizedScore() : 0;
    }

    private String writeJson(AssessmentScores scores) {
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not write assessment snapshot", exception);
        }
    }
}
