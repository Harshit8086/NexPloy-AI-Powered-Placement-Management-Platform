package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.BlockedSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedSlotRepository extends JpaRepository<BlockedSlot, String> {

    List<BlockedSlot> findByCampusId(String campusId);
}
