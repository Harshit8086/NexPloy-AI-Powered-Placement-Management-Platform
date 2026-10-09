package com.nexploy.campuslink.student.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "students")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Dirty-column-only UPDATEs. Background writers (readiness recompute, placement-status
// derivation, assessment snapshots) load the whole row, mutate one or two derived fields and
// save; a full-row write-back from a stale snapshot could otherwise resurrect columns a
// concurrent profile edit already committed — e.g. wiping a just-saved project link.
@DynamicUpdate
public class Student {

    @Id
    private String id;

    private String campusId;
    private String name;
    private String email;
    private String phone;

    // ---- Social links (V69): self-reported profile URLs shown on the profile hero ----
    private String linkedinUrl;
    private String githubUrl;

    private String rollNumber;
    private String branch;
    private String branchCode;
    private Integer graduationYear;
    private BigDecimal cgpa;
    private Integer backlogs;
    private Integer readinessScore;
    private String placementStatus;

    // ---- Directory provenance (V60): how this row entered the system ----
    /** Record provenance: 'DIRECTORY' (officer-managed) or 'SELF_REGISTERED' (legacy/imported). */
    private String source;

    /** Soft-active flag: officers deactivate directory rows instead of deleting them. */
    private Boolean active;

    // ---- Enrichment (V18): eligibility inputs used by drive criteria ----
    private BigDecimal tenthPercentage;
    private BigDecimal twelfthPercentage;

    // ---- Enrichment (V19): profile fields backing the frontend Student shape ----
    private String department;
    private String batch;
    private Integer semester;

    /** Display year label, e.g. "Final Year" ("year" is avoided as a column name). */
    private String yearLabel;

    private Integer backlogHistory;
    private Integer resumeScore;
    private Integer placementProbability;
    private String targetRole;

    /** Convenience quick-display risk; the risk module's risk_entries remain authoritative. */
    private String riskLevel;
    private String riskReason;

    // JSON list/object caches. readiness_snapshots stays the authoritative derived history;
    // breakdownJson / readinessTrendJson are convenience caches like readinessScore.
    @Column(columnDefinition = "text")
    private String strengthsJson;

    @Column(columnDefinition = "text")
    private String criticalGapsJson;

    @Column(columnDefinition = "text")
    private String certificationsJson;

    /** JSON array of {name, stack[], summary}. */
    @Column(columnDefinition = "text")
    private String projectsJson;

    /** JSON array of {company, role, duration, summary}. */
    @Column(columnDefinition = "text")
    private String experienceJson;

    /** JSON object {aptitude, coding, technical, communication, mockInterview}. */
    @Column(columnDefinition = "text")
    private String assessmentsJson;

    // Normalized assessment scores (V40), mirrored from assessmentsJson. The JSON column is
    // retained and remains the authoritative read source until a later phase promotes these.
    private Integer assessmentAptitude;
    private Integer assessmentCoding;
    private Integer assessmentTechnical;
    private Integer assessmentCommunication;
    private Integer assessmentMockInterview;

    /** JSON object of the eight readiness dimensions. */
    @Column(columnDefinition = "text")
    private String breakdownJson;

    /** JSON array of readiness scores over time (sparkline). */
    @Column(columnDefinition = "text")
    private String readinessTrendJson;

    @Column(columnDefinition = "text")
    private String preferredRolesJson;

    @Column(columnDefinition = "text")
    private String preferredDomainsJson;

    @Column(columnDefinition = "text")
    private String preferredLocationsJson;
}
