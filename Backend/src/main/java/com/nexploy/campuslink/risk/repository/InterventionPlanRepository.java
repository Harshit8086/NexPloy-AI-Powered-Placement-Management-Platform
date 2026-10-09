package com.nexploy.campuslink.risk.repository;

import com.nexploy.campuslink.risk.entity.InterventionPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterventionPlanRepository extends JpaRepository<InterventionPlan, String> {

    List<InterventionPlan> findByStudentIdOrderByCreatedAtDesc(String studentId);

    List<InterventionPlan> findByMentorUsernameOrderByCreatedAtDesc(String mentorUsername);
}
