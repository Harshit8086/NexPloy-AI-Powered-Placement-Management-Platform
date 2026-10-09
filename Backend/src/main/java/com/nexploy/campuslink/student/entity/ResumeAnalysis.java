package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A stored record of one Resume Intelligence run for a student (Phase B: Analysis History). One row
 * is written each time the student analyzes an uploaded resume; the newest-first collection powers
 * the history list and progress-over-time view.
 *
 * <p>Only the <em>analysis result</em> is persisted: the deterministic headline {@code overallScore}
 * plus the full structured {@code ResumeAnalysisResponse} serialized into {@code analysisJson}. The
 * raw resume file and its full extracted text are never stored — {@code analysisJson} contains only
 * the short evidence snippets the analysis itself already surfaces. This is descriptive history only
 * and never feeds readiness, skill-gap or digital-twin calculations.
 */
@Entity
@Table(name = "resume_analysis")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAnalysis {

    @Id
    private String id;

    private String studentId;
    private String campusId;

    /** Original filename of the uploaded resume, used to label the run in the history list. */
    private String resumeName;

    /** Deterministic 0-100 headline score at the time of this run. */
    private Integer overallScore;

    /** The full structured {@code ResumeAnalysisResponse} as JSON. Never the raw resume text. */
    @Column(columnDefinition = "text")
    private String analysisJson;

    private OffsetDateTime createdAt;
}
