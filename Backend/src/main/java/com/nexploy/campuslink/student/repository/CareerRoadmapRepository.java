package com.nexploy.campuslink.student.repository;

import com.nexploy.campuslink.student.entity.CareerRoadmap;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRoadmapRepository extends JpaRepository<CareerRoadmap, String> {

    /** The student's single materialized roadmap, or empty if they have not opened one yet. */
    Optional<CareerRoadmap> findByStudentId(String studentId);
}
