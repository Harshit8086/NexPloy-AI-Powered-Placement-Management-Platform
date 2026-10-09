package com.nexploy.campuslink.schedule.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.dto.CreateVenueRequest;
import com.nexploy.campuslink.schedule.dto.UpdateVenueRequest;
import com.nexploy.campuslink.schedule.dto.VenueResponse;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.exception.VenueNotFoundException;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interview-venue administration (create / edit / (de)activate). Officer/admin only; a venue is
 * campus-scoped and never hard-deleted — retiring one flips the {@code active} flag (soft-delete),
 * preserving any schedule history that references it.
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;
    private final CampusAccessService campusAccessService;

    @Transactional
    public VenueResponse create(CreateVenueRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        // Officers are pinned to their assigned campus; only admins may target a requested one.
        String campusId = (user.getRole() == UserRole.ADMIN && request.campusId() != null && !request.campusId().isBlank())
            ? request.campusId()
            : user.getCampusId();
        campusAccessService.requireAccess(user, campusId);

        Venue venue = Venue.builder()
            .id("venue_" + UUID.randomUUID())
            .campusId(campusId)
            .name(request.name().trim())
            .capacity(request.capacity())
            .active(true)
            .build();
        return VenueResponse.from(venueRepository.save(venue));
    }

    @Transactional
    public VenueResponse update(String venueId, UpdateVenueRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Venue venue = loadAccessible(venueId, user);
        venue.setName(request.name().trim());
        venue.setCapacity(request.capacity());
        return VenueResponse.from(venueRepository.save(venue));
    }

    @Transactional
    public VenueResponse setActive(String venueId, boolean active) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        Venue venue = loadAccessible(venueId, user);
        venue.setActive(active);
        return VenueResponse.from(venueRepository.save(venue));
    }

    private Venue loadAccessible(String venueId, UserAccount user) {
        Venue venue = venueRepository.findById(venueId)
            .orElseThrow(() -> new VenueNotFoundException(venueId));
        campusAccessService.requireAccess(user, venue.getCampusId());
        return venue;
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage venues");
        }
    }
}
