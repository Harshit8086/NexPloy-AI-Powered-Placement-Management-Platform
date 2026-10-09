package com.nexploy.campuslink.application.repository;

import com.nexploy.campuslink.application.entity.Application;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, String> {

    List<Application> findByStudentId(String studentId);

    List<Application> findByDriveId(String driveId);

    Optional<Application> findByStudentIdAndDriveId(String studentId, String driveId);

    // ---- Campus-scoped set-based access (applications have no campus_id; scope via students) ----

    /** All applications belonging to the campus's students, in a single join query (no N+1). */
    @Query("select a from Application a "
        + "where a.studentId in (select s.id from Student s where s.campusId = :campusId)")
    List<Application> findByCampusId(@Param("campusId") String campusId);

    @Query("select count(a) from Application a "
        + "where a.studentId in (select s.id from Student s where s.campusId = :campusId)")
    long countByCampusId(@Param("campusId") String campusId);

    /** [status, count] per application status for the campus's students. */
    @Query("select a.status, count(a) from Application a "
        + "where a.studentId in (select s.id from Student s where s.campusId = :campusId) "
        + "group by a.status")
    List<Object[]> countByStatusForCampus(@Param("campusId") String campusId);
}

