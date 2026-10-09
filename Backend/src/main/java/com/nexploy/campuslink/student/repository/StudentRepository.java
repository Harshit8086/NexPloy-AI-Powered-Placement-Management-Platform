package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.Student;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findByCampusId(String campusId);

    /** Paged campus-scoped listing for the officer/admin student roster. */
    Page<Student> findByCampusId(String campusId, Pageable pageable);

    /** The student profile whose email is the signed-in username (STUDENT accounts). */
    Optional<Student> findByEmail(String email);

    /**
     * Case-insensitive per-campus email match, mirroring the uq_students_campus_email functional
     * unique index. Used by directory lookup: registration gating, OTP pre-checks and CSV import
     * duplicate detection.
     */
    @Query("select s from Student s where s.campusId = :campusId and lower(s.email) = lower(:email)")
    Optional<Student> findByCampusIdAndEmailIgnoreCase(@Param("campusId") String campusId,
                                                       @Param("email") String email);

    /**
     * Students matching a registration/roll number (the "studentId" carried by bulk assessment
     * uploads). Returns a list because roll-number uniqueness is not DB-enforced today; the bulk
     * import treats an empty result as STUDENT_NOT_FOUND and more than one match as an error row.
     */
    List<Student> findByRollNumber(String rollNumber);

    // ---- Set-based analytics aggregates (campus-scoped, no rows loaded) ----

    long countByCampusId(String campusId);

    /** Mean readiness over the campus's students, ignoring nulls; null for an empty/all-null cohort. */
    @Query("select avg(s.readinessScore) from Student s "
        + "where s.campusId = :campusId and s.readinessScore is not null")
    Double averageReadinessByCampusId(@Param("campusId") String campusId);

    /** [placementStatus, count] per status for the campus; a null status is folded to UNKNOWN by the caller. */
    @Query("select s.placementStatus, count(s) from Student s where s.campusId = :campusId "
        + "group by s.placementStatus")
    List<Object[]> countByPlacementStatusForCampus(@Param("campusId") String campusId);


    /**
     * Store a skill embedding (pgvector). The vector arrives as its text literal (e.g. "[0.1,...]")
     * and is cast to the column type; {@code null} clears it. Native because {@code vector} is not a
     * JPA-mapped type — the column is intentionally not on the {@link Student} entity, so
     * {@code ddl-auto: validate} ignores it.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE students SET skills_embedding = CAST(:embedding AS vector) WHERE id = :id",
        nativeQuery = true)
    void updateSkillsEmbedding(@Param("id") String id, @Param("embedding") String embedding);

    /** True once a skill embedding has been stored for this student. */
    @Query(value = "SELECT skills_embedding IS NOT NULL FROM students WHERE id = :id", nativeQuery = true)
    Boolean hasSkillsEmbedding(@Param("id") String id);

    /**
     * Cosine similarity (0-100) between this student's skill embedding and the drive's required-skill
     * embedding, computed DB-side with the pgvector cosine-distance operator ({@code 1 - dist}).
     * Returns {@code null} when either embedding is absent — missing evidence, not zero similarity.
     */
    @Query(value = """
        SELECT ROUND((GREATEST(0, LEAST(1, 1 - (s.skills_embedding <=> d.required_skills_embedding))) * 100)::numeric, 2)
        FROM students s, drives d
        WHERE s.id = :studentId AND d.id = :driveId
          AND s.skills_embedding IS NOT NULL AND d.required_skills_embedding IS NOT NULL
        """, nativeQuery = true)
    Double semanticSimilarityPct(@Param("driveId") String driveId, @Param("studentId") String studentId);

    /**
     * Store a projects embedding (pgvector): the student's recorded project text (names, summaries,
     * stacks). Same text-literal contract as {@link #updateSkillsEmbedding}; the column is
     * intentionally not on the {@link Student} entity so {@code ddl-auto: validate} ignores it.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE students SET projects_embedding = CAST(:embedding AS vector) WHERE id = :id",
        nativeQuery = true)
    void updateProjectsEmbedding(@Param("id") String id, @Param("embedding") String embedding);

    /** True once a projects embedding has been stored for this student. */
    @Query(value = "SELECT projects_embedding IS NOT NULL FROM students WHERE id = :id", nativeQuery = true)
    Boolean hasProjectsEmbedding(@Param("id") String id);

    /**
     * Cosine similarity (0-100) between this student's projects embedding and the drive's
     * description embedding — the deep project-JD relevance signal. Same pgvector cosine contract as
     * {@link #semanticSimilarityPct}: {@code null} when either embedding is absent, so missing
     * evidence is excluded from the fit score rather than imputed as zero.
     */
    @Query(value = """
        SELECT ROUND((GREATEST(0, LEAST(1, 1 - (s.projects_embedding <=> d.description_embedding))) * 100)::numeric, 2)
        FROM students s, drives d
        WHERE s.id = :studentId AND d.id = :driveId
          AND s.projects_embedding IS NOT NULL AND d.description_embedding IS NOT NULL
        """, nativeQuery = true)
    Double projectRelevancePct(@Param("driveId") String driveId, @Param("studentId") String studentId);
}
