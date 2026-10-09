package com.nexploy.campuslink.schedule.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.schedule.dto.CreateVenueRequest;
import com.nexploy.campuslink.schedule.dto.SetActiveRequest;
import com.nexploy.campuslink.schedule.dto.UpdateVenueRequest;
import com.nexploy.campuslink.schedule.dto.VenueResponse;
import com.nexploy.campuslink.schedule.repository.VenueRepository;
import com.nexploy.campuslink.schedule.service.VenueService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/schedule/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueRepository venueRepository;
    private final CampusAccessService campusAccessService;
    private final VenueService venueService;

    @GetMapping
    public List<VenueResponse> listVenues() {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see every campus's venues; officers are scoped to their own campus.
        if (user.getRole() == UserRole.ADMIN) {
            return venueRepository.findAll().stream().map(VenueResponse::from).toList();
        }

        return venueRepository.findByCampusId(user.getCampusId()).stream()
            .map(VenueResponse::from)
            .toList();
    }

    /** Create an interview venue (officer/admin). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse createVenue(@Valid @RequestBody CreateVenueRequest request) {
        return venueService.create(request);
    }

    /** Replace a venue's editable fields (officer/admin). */
    @PutMapping("/{venueId}")
    public VenueResponse updateVenue(
        @PathVariable String venueId,
        @Valid @RequestBody UpdateVenueRequest request
    ) {
        return venueService.update(venueId, request);
    }

    /** (De)activate a venue — soft-delete via the active flag (officer/admin). */
    @PatchMapping("/{venueId}/active")
    public VenueResponse setActive(
        @PathVariable String venueId,
        @Valid @RequestBody SetActiveRequest request
    ) {
        return venueService.setActive(venueId, request.active());
    }
}
