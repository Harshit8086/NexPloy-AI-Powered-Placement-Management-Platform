package com.nexploy.campuslink.campus.controller;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.campus.dto.CampusResponse;
import com.nexploy.campuslink.campus.dto.CreateCampusRequest;
import com.nexploy.campuslink.campus.dto.SetCampusActiveRequest;
import com.nexploy.campuslink.campus.dto.UpdateCampusRequest;
import com.nexploy.campuslink.campus.exception.CampusNotFoundException;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import com.nexploy.campuslink.campus.service.CampusService;
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
@RequestMapping("/api/campuses")
@RequiredArgsConstructor
public class CampusController {

    private final CampusRepository campusRepository;
    private final CampusAccessService campusAccessService;
    private final CampusService campusService;

    /**
     * Public list of active campuses, used to populate the registration form's campus picker
     * before a user has an account. Returns only active campuses and no privileged fields;
     * permitted without authentication in {@code SecurityConfig}.
     */
    @GetMapping("/public")
    public List<CampusResponse> listPublicCampuses() {
        return campusRepository.findAll().stream()
            .filter(campus -> campus.isActive())
            .map(CampusResponse::from)
            .toList();
    }

    @GetMapping
    public List<CampusResponse> listCampuses() {
        UserAccount user = campusAccessService.currentUser();
        // Only admins see the full campus list; officers (like everyone else) get their own.
        if (user.getRole() == UserRole.ADMIN) {
            return campusRepository.findAll().stream().map(CampusResponse::from).toList();
        }

        return campusRepository.findById(user.getCampusId())
            .map(campus -> List.of(CampusResponse.from(campus)))
            .orElse(List.of());
    }

    @GetMapping("/{campusId}")
    public CampusResponse getCampus(@PathVariable String campusId) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);

        return campusRepository.findById(campusId)
            .map(CampusResponse::from)
            .orElseThrow(() -> new CampusNotFoundException(campusId));
    }

    /** Create a campus (admin only). Id is client-supplied per the varchar-PK convention. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampusResponse createCampus(@Valid @RequestBody CreateCampusRequest request) {
        return campusService.create(request);
    }

    /** Replace a campus's editable fields (admin only). */
    @PutMapping("/{campusId}")
    public CampusResponse updateCampus(
        @PathVariable String campusId,
        @Valid @RequestBody UpdateCampusRequest request
    ) {
        return campusService.update(campusId, request);
    }

    /** (De)activate a campus — soft-delete via the active flag (admin only). */
    @PatchMapping("/{campusId}/active")
    public CampusResponse setActive(
        @PathVariable String campusId,
        @Valid @RequestBody SetCampusActiveRequest request
    ) {
        return campusService.setActive(campusId, request.active());
    }
}
