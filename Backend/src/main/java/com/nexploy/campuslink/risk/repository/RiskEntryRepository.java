package com.nexploy.campuslink.risk.repository;

import com.nexploy.campuslink.risk.entity.RiskEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskEntryRepository extends JpaRepository<RiskEntry, String> {

    List<RiskEntry> findByStudentIdOrderByGeneratedAtDesc(String studentId);

    /** All entries for a campus, newest first — the caller keeps the latest per student. */
    List<RiskEntry> findByCampusIdOrderByGeneratedAtDesc(String campusId);
}
