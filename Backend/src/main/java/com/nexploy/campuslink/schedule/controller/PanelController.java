package com.nexploy.campuslink.schedule.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.dto.CreatePanelRequest;
import com.nexploy.campuslink.schedule.dto.PanelResponse;
import com.nexploy.campuslink.schedule.dto.SetActiveRequest;
import com.nexploy.campuslink.schedule.dto.UpdatePanelRequest;
import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.PanelMember;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import com.nexploy.campuslink.schedule.service.PanelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule/panels")
@RequiredArgsConstructor
public class PanelController {

    private final PanelRepository panelRepository;
    private final PanelMemberRepository panelMemberRepository;
    private final CampusAccessService campusAccessService;
    private final PanelService panelService;

    @GetMapping
    public List<PanelResponse> listPanels() {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's panels; officers are scoped to their own campus.
        List<Panel> panels = user.getRole() == UserRole.ADMIN
            ? panelRepository.findAll()
            : panelRepository.findByCampusId(user.getCampusId());

        if (panels.isEmpty()) {
            return List.of();
        }

        Map<String, List<PanelMember>> membersByPanel = panelMemberRepository
            .findByPanelIdIn(panels.stream().map(Panel::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(PanelMember::getPanelId));

        return panels.stream()
            .map(panel -> PanelResponse.from(panel, membersByPanel.getOrDefault(panel.getId(), List.of())))
            .toList();
    }

    /** Create an interview panel with an optional member roster (officer/admin). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PanelResponse createPanel(@Valid @RequestBody CreatePanelRequest request) {
        return panelService.create(request);
    }

    /** Replace a panel's editable fields; a non-null members list replaces the roster (officer/admin). */
    @PutMapping("/{panelId}")
    public PanelResponse updatePanel(
        @PathVariable String panelId,
        @Valid @RequestBody UpdatePanelRequest request
    ) {
        return panelService.update(panelId, request);
    }

    /** (De)activate a panel — soft-delete via the active flag (officer/admin). */
    @PatchMapping("/{panelId}/active")
    public PanelResponse setActive(
        @PathVariable String panelId,
        @Valid @RequestBody SetActiveRequest request
    ) {
        return panelService.setActive(panelId, request.active());
    }
}
