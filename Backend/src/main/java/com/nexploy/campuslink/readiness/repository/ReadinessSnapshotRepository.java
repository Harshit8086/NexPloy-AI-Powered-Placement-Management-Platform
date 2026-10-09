package com.nexploy.campuslink.readiness.repository;

import com.nexploy.campuslink.readiness.entity.ReadinessSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadinessSnapshotRepository extends JpaRepository<ReadinessSnapshot, String> {

    List<ReadinessSnapshot> findByStudentIdOrderByGeneratedAtDesc(String studentId);
}
