package com.nexploy.campuslink.schedule.repository;

import com.nexploy.campuslink.schedule.entity.PanelMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PanelMemberRepository extends JpaRepository<PanelMember, String> {

    List<PanelMember> findByPanelId(String panelId);

    List<PanelMember> findByPanelIdIn(List<String> panelIds);
}
