package com.nexploy.campuslink.interview.repository;

import com.nexploy.campuslink.interview.entity.Interview;
import com.nexploy.campuslink.interview.entity.InterviewStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, String> {

    List<Interview> findByCampusId(String campusId);

    /** Paged campus-scoped listing for the interviews roster. */
    Page<Interview> findByCampusId(String campusId, Pageable pageable);

    List<Interview> findByStudentId(String studentId);

    List<Interview> findByDriveId(String driveId);

    /** Interviews in a given status whose start falls in [from, to] — powers the reminder scheduler. */
    List<Interview> findByStatusAndScheduledAtBetween(
        InterviewStatus status, OffsetDateTime from, OffsetDateTime to);
}
