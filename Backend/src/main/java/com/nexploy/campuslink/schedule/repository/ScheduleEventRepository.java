package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.ScheduleEvent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, String> {

    List<ScheduleEvent> findByCampusId(String campusId);
}

