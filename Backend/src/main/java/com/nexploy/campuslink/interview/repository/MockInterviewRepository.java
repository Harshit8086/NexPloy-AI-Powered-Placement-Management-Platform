package com.nexploy.campuslink.interview.repository;

import com.nexploy.campuslink.interview.entity.MockInterview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockInterviewRepository extends JpaRepository<MockInterview, String> {

    /** A student's practice runs, newest first, for the history list. */
    List<MockInterview> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
