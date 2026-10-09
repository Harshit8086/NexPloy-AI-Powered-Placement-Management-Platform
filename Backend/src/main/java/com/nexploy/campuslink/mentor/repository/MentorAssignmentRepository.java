package com.nexploy.campuslink.mentor.repository;

import com.nexploy.campuslink.mentor.entity.MentorAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MentorAssignmentRepository extends JpaRepository<MentorAssignment, String> {

    List<MentorAssignment> findByMentorUsername(String mentorUsername);

    List<MentorAssignment> findByStudentId(String studentId);

    boolean existsByMentorUsernameAndStudentId(String mentorUsername, String studentId);
}
