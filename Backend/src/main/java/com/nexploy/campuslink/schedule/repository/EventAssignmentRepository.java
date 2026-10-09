package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.EventAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAssignmentRepository extends JpaRepository<EventAssignment, String> {

    List<EventAssignment> findByEventId(String eventId);

    List<EventAssignment> findByStudentId(String studentId);

    List<EventAssignment> findByCampusId(String campusId);

    /** Removes every candidate assignment for a mirror event (called before the event row is deleted). */
    void deleteByEventId(String eventId);
}
