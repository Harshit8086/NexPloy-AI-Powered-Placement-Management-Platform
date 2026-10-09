package com.nexploy.campuslink.campus.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.campus.dto.CampusResponse;
import com.nexploy.campuslink.campus.dto.CreateCampusRequest;
import com.nexploy.campuslink.campus.dto.UpdateCampusRequest;
import com.nexploy.campuslink.campus.entity.Campus;
import com.nexploy.campuslink.campus.exception.CampusNotFoundException;
import com.nexploy.campuslink.campus.repository.CampusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campus administration (create / edit / (de)activate). Admin-only: campuses are the tenant
 * boundary, so their lifecycle is the platform administrator's, not an officer's. Deactivation
 * is a soft-delete via the {@code active} flag — a campus is never hard-deleted.
 */
@Service
@RequiredArgsConstructor
public class CampusService {

    private final CampusRepository campusRepository;
    private final CampusAccessService campusAccessService;

    @Transactional
    public CampusResponse create(CreateCampusRequest request) {
        requireAdmin();
        if (campusRepository.existsById(request.id())) {
            throw new IllegalArgumentException("Campus already exists: " + request.id());
        }
        Campus campus = Campus.builder()
            .id(request.id())
            .name(request.name())
            .city(request.city())
            .state(request.state())
            .active(true)
            .build();
        return CampusResponse.from(campusRepository.save(campus));
    }

    @Transactional
    public CampusResponse update(String campusId, UpdateCampusRequest request) {
        requireAdmin();
        Campus campus = load(campusId);
        campus.setName(request.name());
        campus.setCity(request.city());
        campus.setState(request.state());
        return CampusResponse.from(campusRepository.save(campus));
    }

    @Transactional
    public CampusResponse setActive(String campusId, boolean active) {
        requireAdmin();
        Campus campus = load(campusId);
        campus.setActive(active);
        return CampusResponse.from(campusRepository.save(campus));
    }

    private Campus load(String campusId) {
        return campusRepository.findById(campusId)
            .orElseThrow(() -> new CampusNotFoundException(campusId));
    }

    private void requireAdmin() {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only admins can manage campuses");
        }
    }
}
