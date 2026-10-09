package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.ResumeAnalysis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, String> {

    /** A student's analysis runs, newest first, for the history list. */
    List<ResumeAnalysis> findByStudentIdOrderByCreatedAtDesc(String studentId);

    /**
     * The student's single most recent analysis run (limit 1) — used for roadmap seeding, so we fetch
     * only the row we need rather than loading the whole history to take its head.
     */
    Optional<ResumeAnalysis> findTopByStudentIdOrderByCreatedAtDesc(String studentId);

    /** One run, scoped to its owner so a student can only ever open their own analysis. */
    Optional<ResumeAnalysis> findByIdAndStudentId(String id, String studentId);
}
