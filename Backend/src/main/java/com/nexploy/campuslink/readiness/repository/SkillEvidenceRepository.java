package com.nexploy.campuslink.readiness.repository;

import com.nexploy.campuslink.readiness.entity.SkillEvidence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillEvidenceRepository extends JpaRepository<SkillEvidence, String> {

    List<SkillEvidence> findByStudentIdOrderByRecordedAtDesc(String studentId);
}
