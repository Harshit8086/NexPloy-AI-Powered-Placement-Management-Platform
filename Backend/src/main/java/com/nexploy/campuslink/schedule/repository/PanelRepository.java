package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.Panel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelRepository extends JpaRepository<Panel, String> {

    List<Panel> findByCampusId(String campusId);
}
