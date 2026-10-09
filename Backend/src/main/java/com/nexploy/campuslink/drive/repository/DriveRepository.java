package com.nexploy.campuslink.drive.repository;

import com.nexploy.campuslink.drive.entity.Drive;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DriveRepository extends JpaRepository<Drive, String> {

    List<Drive> findByCampusId(String campusId);

    /** Paged campus-scoped listing for the drive roster. */
    Page<Drive> findByCampusId(String campusId, Pageable pageable);

    List<Drive> findByRecruiterId(String recruiterId);

    // ---- Set-based analytics aggregates ----

    long countByCampusId(String campusId);

    /** [status, count] per drive status for the campus. */
    @Query("select d.status, count(d) from Drive d where d.campusId = :campusId group by d.status")
    List<Object[]> countByStatusForCampus(@Param("campusId") String campusId);

    /**
     * Store the drive's required-skills embedding (pgvector). The vector arrives as its text literal
     * and is cast to the column type; {@code null} clears it. Native because {@code vector} is not a
     * JPA-mapped type — the column is intentionally not on the {@link Drive} entity, so
     * {@code ddl-auto: validate} ignores it.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE drives SET required_skills_embedding = CAST(:embedding AS vector) WHERE id = :id",
        nativeQuery = true)
    void updateRequiredSkillsEmbedding(@Param("id") String id, @Param("embedding") String embedding);

    /** True once a required-skills embedding has been stored for this drive. */
    @Query(value = "SELECT required_skills_embedding IS NOT NULL FROM drives WHERE id = :id",
        nativeQuery = true)
    Boolean hasRequiredSkillsEmbedding(@Param("id") String id);

    /**
     * Store the drive's description/role embedding (pgvector), the text side of deep project-JD
     * matching. Same text-literal contract as {@link #updateRequiredSkillsEmbedding}: native because
     * {@code vector} is not a JPA-mapped type, and the column is intentionally not on the
     * {@link Drive} entity so {@code ddl-auto: validate} ignores it.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE drives SET description_embedding = CAST(:embedding AS vector) WHERE id = :id",
        nativeQuery = true)
    void updateDescriptionEmbedding(@Param("id") String id, @Param("embedding") String embedding);

    /** True once a description embedding has been stored for this drive. */
    @Query(value = "SELECT description_embedding IS NOT NULL FROM drives WHERE id = :id", nativeQuery = true)
    Boolean hasDescriptionEmbedding(@Param("id") String id);
}
