package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.ScheduleConflictRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleConflictRepository extends JpaRepository<ScheduleConflictRecord, String> {

    List<ScheduleConflictRecord> findByCampusId(String campusId);

    void deleteByCampusId(String campusId);
}
