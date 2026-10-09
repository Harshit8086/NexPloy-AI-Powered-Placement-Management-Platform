package com.nexploy.campuslink.schedule.dto;

import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.PanelMember;
import java.util.List;

public record PanelResponse(
    String id,
    String campusId,
    String name,
    boolean active,
    List<Member> members
) {

    public record Member(String id, String name, String email) {

        public static Member from(PanelMember member) {
            return new Member(member.getId(), member.getMemberName(), member.getMemberEmail());
        }
    }

    public static PanelResponse from(Panel panel, List<PanelMember> members) {
        return new PanelResponse(
            panel.getId(),
            panel.getCampusId(),
            panel.getName(),
            panel.isActive(),
            members.stream().map(Member::from).toList()
        );
    }
}
