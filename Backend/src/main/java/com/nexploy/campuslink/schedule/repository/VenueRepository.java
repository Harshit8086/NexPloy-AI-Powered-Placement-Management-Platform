package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.Venue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, String> {

    List<Venue> findByCampusId(String campusId);
}
