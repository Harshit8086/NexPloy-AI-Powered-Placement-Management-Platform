package com.nexploy.campuslink.schedule.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.dto.CreatePanelRequest;
import com.nexploy.campuslink.schedule.dto.CreatePanelRequest.PanelMemberInput;
import com.nexploy.campuslink.schedule.dto.PanelResponse;
import com.nexploy.campuslink.schedule.dto.UpdatePanelRequest;
import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.PanelMember;
import com.nexploy.campuslink.schedule.exception.PanelNotFoundException;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interview-panel administration (create / edit / (de)activate). Officer/admin only; a panel is
 * campus-scoped and never hard-deleted — retiring one flips the {@code active} flag (soft-delete).
 * Member rosters are replaced wholesale on create/update; members carry a server-assigned id.
 */
@Service
@RequiredArgsConstructor
public class PanelService {

    private final PanelRepository panelRepository;
    private final PanelMemberRepository panelMemberRepository;
    private final CampusAccessService campusAccessService;

    @Transactional
    public PanelResponse create(CreatePanelRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        // Officers are pinned to their assigned campus; only admins may target a requested one.
        String campusId = (user.getRole() == UserRole.ADMIN && request.campusId() != null && !request.campusId().isBlank())
            ? request.campusId()
            : user.getCampusId();
        campusAccessService.requireAccess(user, campusId);

        Panel panel = panelRepository.save(Panel.builder()
            .id("panel_" + UUID.randomUUID())
            .campusId(campusId)
            .name(request.name().trim())
            .active(true)
            .build());

        List<PanelMember> members = saveMembers(panel.getId(), request.members());
        return PanelResponse.from(panel, members);
    }

    @Transactional
    public PanelResponse update(String panelId, UpdatePanelRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Panel panel = loadAccessible(panelId, user);
        panel.setName(request.name().trim());
        panelRepository.save(panel);

        List<PanelMember> members;
        if (request.members() != null) {
            panelMemberRepository.deleteAll(panelMemberRepository.findByPanelId(panelId));
            members = saveMembers(panelId, request.members());
        } else {
            members = panelMemberRepository.findByPanelId(panelId);
        }
        return PanelResponse.from(panel, members);
    }

    @Transactional
    public PanelResponse setActive(String panelId, boolean active) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Panel panel = loadAccessible(panelId, user);
        panel.setActive(active);
        panelRepository.save(panel);
        return PanelResponse.from(panel, panelMemberRepository.findByPanelId(panelId));
    }

    private List<PanelMember> saveMembers(String panelId, List<PanelMemberInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<PanelMember> members = inputs.stream()
            .map(input -> PanelMember.builder()
                .id("pm_" + UUID.randomUUID())
                .panelId(panelId)
                .memberName(input.name().trim())
                .memberEmail(input.email() == null ? null : input.email().trim())
                .build())
            .toList();
        return panelMemberRepository.saveAll(members);
    }

    private Panel loadAccessible(String panelId, UserAccount user) {
        Panel panel = panelRepository.findById(panelId)
            .orElseThrow(() -> new PanelNotFoundException(panelId));
        campusAccessService.requireAccess(user, panel.getCampusId());
        return panel;
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage panels");
        }
    }
}
