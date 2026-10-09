package com.nexploy.campuslink.aptitude.repository;

import com.nexploy.campuslink.aptitude.entity.AptitudeQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestionSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AptitudeQuestionRepository extends JpaRepository<AptitudeQuestion, String> {

    /**
     * Official-test draw pool: active bank rows only. The source filter plus the studentId IS NULL
     * guard keep AI-generated practice questions out of graded tests, and the campus clause admits
     * global questions (campusId NULL) plus the student's own campus questions.
     */
    @Query("select q from AptitudeQuestion q where q.active = true and q.source in :sources "
        + "and q.studentId is null and (q.campusId is null or q.campusId = :campusId) "
        + "and (:category is null or q.category = :category)")
    List<AptitudeQuestion> findOfficialBank(@Param("sources") List<AptitudeQuestionSource> sources,
                                            @Param("campusId") String campusId,
                                            @Param("category") String category);

    /** Bank size per category for the availability check on the test catalog. */
    @Query("select count(q) from AptitudeQuestion q where q.active = true and q.source in :sources "
        + "and q.studentId is null and (q.campusId is null or q.campusId = :campusId) "
        + "and q.category = :category")
    long countOfficialBank(@Param("sources") List<AptitudeQuestionSource> sources,
                           @Param("campusId") String campusId,
                           @Param("category") String category);

    /** Officer bank listing: bank-sourced questions for the campus, optionally one category. */
    @Query("select q from AptitudeQuestion q where q.source in :sources "
        + "and q.studentId is null and (q.campusId is null or q.campusId = :campusId) "
        + "and (:category is null or q.category = :category) order by q.createdAt desc")
    List<AptitudeQuestion> findBank(@Param("sources") List<AptitudeQuestionSource> sources,
                                    @Param("campusId") String campusId,
                                    @Param("category") String category);
}
