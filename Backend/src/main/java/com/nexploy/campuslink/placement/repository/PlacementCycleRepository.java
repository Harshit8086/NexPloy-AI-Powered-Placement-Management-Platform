package com.nexploy.campuslink.placement.repository;

import com.nexploy.campuslink.placement.entity.PlacementCycle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementCycleRepository extends JpaRepository<PlacementCycle, String> {

    List<PlacementCycle> findByCampusId(String campusId);
}
