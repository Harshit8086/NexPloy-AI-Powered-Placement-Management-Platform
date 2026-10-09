package com.nexploy.campuslink.student.dto;

import com.nexploy.campuslink.student.entity.Student;

import java.math.BigDecimal;
import java.util.List;

/**
 * Frontend-facing view of a student (camelCase). Enriched to back the profile / digital-twin /
 * readiness screens. Scalars come straight off the entity; the list/object fields are parsed
 * from the entity's JSON columns by {@code StudentService}. {@code readinessScore} and
 * {@code breakdown} are convenience projections of the student's current readiness; the
 * authoritative derived-score history lives in readiness_snapshots.
 *
 * <p>Not included here (served elsewhere or derived): skillGaps ({@code POST
 * /students/{id}/skill-gap}), roadmap (not yet modelled), and activeDrives/applicationsCount
 * (aggregate counts). The frontend mapper composes its nested {@code criteria}-style shapes
 * from these flat fields.
 */
public record StudentResponse(
    String id,
    String campusId,
    String name,
    String email,
    String phone,
    String linkedinUrl,
    String githubUrl,
    String rollNumber,
    String department,
    String branch,
    String branchCode,
    String batch,
    Integer semester,
    Integer graduationYear,
    String year,
    BigDecimal cgpa,
    BigDecimal tenthPercentage,
    BigDecimal twelfthPercentage,
    Integer backlogs,
    Integer backlogHistory,
    Integer readinessScore,
    Integer placementProbability,
    Integer resumeScore,
    String targetRole,
    String riskLevel,
    String riskReason,
    String placementStatus,
    List<String> strengths,
    List<String> criticalGaps,
    List<String> certifications,
    List<StudentProject> projects,
    List<StudentExperience> experience,
    AssessmentScores assessments,
    ReadinessBreakdown breakdown,
    List<Integer> readinessTrend,
    List<String> preferredRoles,
    List<String> preferredDomains,
    List<String> preferredLocations,
    // ---- Directory provenance (V60): how this record entered the system ----
    String source,
    Boolean active
) {

    /**
     * Builds the response from the entity plus the parsed JSON fields. {@code StudentService}
     * owns JSON parsing so this DTO stays free of serialization concerns.
     */
    public static StudentResponse from(
        Student student,
        List<String> strengths,
        List<String> criticalGaps,
        List<String> certifications,
        List<StudentProject> projects,
        List<StudentExperience> experience,
        AssessmentScores assessments,
        ReadinessBreakdown breakdown,
        List<Integer> readinessTrend,
        List<String> preferredRoles,
        List<String> preferredDomains,
        List<String> preferredLocations
    ) {
        return new StudentResponse(
            student.getId(),
            student.getCampusId(),
            student.getName(),
            student.getEmail(),
            student.getPhone(),
            // Social links (V69): null until the student adds them; the client renders them only when set.
            student.getLinkedinUrl(),
            student.getGithubUrl(),
            student.getRollNumber(),
            student.getDepartment(),
            student.getBranch(),
            // cgpa/backlogs/branchCode are non-null in the frontend contract (StudentResponseDto),
            // and the mapper copies them through unguarded — but a freshly self-registered student
            // has no academic profile yet, so coalesce to safe defaults here rather than emit nulls
            // the client would crash on (e.g. cgpa.toFixed in the eligibility engine).
            student.getBranchCode() != null ? student.getBranchCode() : "",
            student.getBatch(),
            student.getSemester(),
            student.getGraduationYear(),
            student.getYearLabel(),
            student.getCgpa() != null ? student.getCgpa() : BigDecimal.ZERO,
            student.getTenthPercentage(),
            student.getTwelfthPercentage(),
            student.getBacklogs() != null ? student.getBacklogs() : 0,
            student.getBacklogHistory(),
            student.getReadinessScore(),
            student.getPlacementProbability(),
            student.getResumeScore(),
            student.getTargetRole(),
            student.getRiskLevel(),
            student.getRiskReason(),
            student.getPlacementStatus(),
            strengths,
            criticalGaps,
            certifications,
            projects,
            experience,
            assessments,
            breakdown,
            readinessTrend,
            preferredRoles,
            preferredDomains,
            preferredLocations,
            // Directory provenance: coalesce so rows predating V60 still render.
            student.getSource() != null ? student.getSource() : "SELF_REGISTERED",
            student.getActive() != null ? student.getActive() : Boolean.TRUE
        );
    }
}
