package com.nexploy.campuslink.schedule.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.dto.ScheduleEventResponse;
import com.nexploy.campuslink.schedule.entity.Panel;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.repository.PanelRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule/events")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleEventRepository scheduleEventRepository;
    private final VenueRepository venueRepository;
    private final PanelRepository panelRepository;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<ScheduleEventResponse> listScheduleEvents() {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's schedule; officers are scoped to their own campus.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        List<ScheduleEvent> events = privileged
            ? scheduleEventRepository.findAll()
            : scheduleEventRepository.findByCampusId(user.getCampusId());
        if (events.isEmpty()) {
            return List.of();
        }

        Map<String, String> venueNames = (privileged
            ? venueRepository.findAll()
            : venueRepository.findByCampusId(user.getCampusId())).stream()
            .collect(Collectors.toMap(Venue::getId, Venue::getName, (left, right) -> left));
        Map<String, String> panelNames = (privileged
            ? panelRepository.findAll()
            : panelRepository.findByCampusId(user.getCampusId())).stream()
            .collect(Collectors.toMap(Panel::getId, Panel::getName, (left, right) -> left));

        return events.stream()
            .map(event -> ScheduleEventResponse.from(
                event,
                venueNames.get(event.getVenueId()),
                panelNames.get(event.getPanelId())))
            .toList();
    }
}
