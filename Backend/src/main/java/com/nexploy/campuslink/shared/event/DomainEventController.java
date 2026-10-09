package com.nexploy.campuslink.shared.event;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to the recorded domain-event audit trail, scoped to a campus (DESIGN.md section 7).
 * Campus access is enforced the same way as the rest of the platform: officers/admins cross-campus,
 * others only their own campus.
 */
@RestController
@RequestMapping("/api/campuses/{campusId}/events")
@RequiredArgsConstructor
public class DomainEventController {

    private final DomainEventRecordRepository repository;
    private final CampusAccessService campusAccessService;

    @GetMapping
    public List<DomainEventResponse> list(@PathVariable String campusId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);
        return repository.findByCampusIdOrderByOccurredAtDesc(campusId).stream()
            .map(DomainEventResponse::from).toList();
    }
}
