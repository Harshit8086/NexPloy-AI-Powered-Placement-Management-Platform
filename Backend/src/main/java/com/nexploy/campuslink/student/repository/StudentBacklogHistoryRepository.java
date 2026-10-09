package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.StudentBacklogHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentBacklogHistoryRepository extends JpaRepository<StudentBacklogHistory, String> {

    /** A student's backlog timeline, newest first. */
    List<StudentBacklogHistory> findByStudentIdOrderByRecordedAtDesc(String studentId);
}
