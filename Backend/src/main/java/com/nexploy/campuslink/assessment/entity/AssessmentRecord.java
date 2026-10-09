package com.nexploy.campuslink.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One dated assessment result for a student, ingested by the placement-officer bulk import.
 *
 * <p>This is the append-only history: every distinct (student, type, name, date) is its own row,
 * so multiple tests of the same type over time are all preserved. The student's current
 * five-field snapshot ({@code students.assessments_json} + the {@code assessment_*} columns) is
 * recomputed from these rows as the LATEST test per {@link AssessmentType}. The business key
 * {@code (student_id, assessment_type, assessment_name, assessment_date)} is unique, so
 * re-uploading the same file updates the scores in place rather than creating duplicates.
 */
@Entity
@Table(
    name = "assessment_records",
    uniqueConstraints = @UniqueConstraint(
        name = "assessment_records_unique",
        columnNames = {"student_id", "assessment_type", "assessment_name", "assessment_date"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentRecord {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private AssessmentType assessmentType;

    private String assessmentName;

    /** Raw marks scored, as reported in the upload. */
    @Column(precision = 6, scale = 2)
    private BigDecimal rawScore;

    /** Maximum possible marks; used to normalize {@code rawScore} to the 0-100 scale. */
    @Column(precision = 6, scale = 2)
    private BigDecimal maxScore;

    /** {@code round(rawScore / maxScore * 100)}, clamped to 0..100 — the snapshot scale. */
    private Integer normalizedScore;

    private LocalDate assessmentDate;

    /** How the row was recorded; bulk imports use {@code BULK_IMPORT}. */
    private String source;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
