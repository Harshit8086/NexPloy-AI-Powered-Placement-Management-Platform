package com.nexploy.campuslink.placement.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.placement.dto.CreatePlacementCycleRequest;
import com.nexploy.campuslink.placement.dto.PlacementCycleResponse;
import com.nexploy.campuslink.placement.dto.UpdatePlacementCycleRequest;
import com.nexploy.campuslink.placement.entity.PlacementCycle;
import com.nexploy.campuslink.placement.exception.InvalidCycleTransitionException;
import com.nexploy.campuslink.placement.exception.PlacementCycleNotFoundException;
import com.nexploy.campuslink.placement.repository.PlacementCycleRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Placement cycle CRUD. Officers/admins own cycles; a cycle is campus-scoped and its status
 * follows PLANNING -> ACTIVE -> CLOSED -> ARCHIVED (with a CLOSED -> ACTIVE reopen). Cycles are
 * never hard-deleted — retiring one is an ARCHIVED transition, preserving grouped history.
 */
@Service
@RequiredArgsConstructor
public class PlacementCycleService {

    private final PlacementCycleRepository placementCycleRepository;
    private final CampusAccessService campusAccessService;

    private static final Set<String> STATUSES = Set.of("PLANNING", "ACTIVE", "CLOSED", "ARCHIVED");

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "PLANNING", Set.of("ACTIVE", "ARCHIVED"),
        "ACTIVE", Set.of("CLOSED", "ARCHIVED"),
        "CLOSED", Set.of("ACTIVE", "ARCHIVED"),
        "ARCHIVED", Set.of()
    );

    @Transactional(readOnly = true)
    public List<PlacementCycleResponse> list(String campusId) {
        UserAccount user = campusAccessService.currentUser();
        // Only admins range across campuses; officers see their own campus's cycles.
        boolean privileged = user.getRole() == UserRole.ADMIN;

        List<PlacementCycle> cycles;
        if (privileged) {
            cycles = campusId == null || campusId.isBlank()
                ? placementCycleRepository.findAll()
                : placementCycleRepository.findByCampusId(campusId);
        } else {
            cycles = placementCycleRepository.findByCampusId(user.getCampusId());
        }
        return cycles.stream().map(PlacementCycleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlacementCycleResponse get(String cycleId) {
        UserAccount user = campusAccessService.currentUser();
        return PlacementCycleResponse.from(loadAccessible(cycleId, user));
    }

    @Transactional
    public PlacementCycleResponse create(CreatePlacementCycleRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        // Officers are pinned to their assigned campus; only admins may target a requested one.
        String campusId = (user.getRole() == UserRole.ADMIN && request.campusId() != null && !request.campusId().isBlank())
            ? request.campusId()
            : user.getCampusId();
        campusAccessService.requireAccess(user, campusId);

        PlacementCycle cycle = PlacementCycle.builder()
            .id("cyc_" + UUID.randomUUID())
            .campusId(campusId)
            .name(request.name())
            .academicYear(request.academicYear())
            .startsOn(request.startsOn())
            .endsOn(request.endsOn())
            .status("PLANNING")
            .build();
        return PlacementCycleResponse.from(placementCycleRepository.save(cycle));
    }

    @Transactional
    public PlacementCycleResponse update(String cycleId, UpdatePlacementCycleRequest request) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        PlacementCycle cycle = loadAccessible(cycleId, user);
        cycle.setName(request.name());
        cycle.setAcademicYear(request.academicYear());
        cycle.setStartsOn(request.startsOn());
        cycle.setEndsOn(request.endsOn());
        return PlacementCycleResponse.from(placementCycleRepository.save(cycle));
    }

    @Transactional
    public PlacementCycleResponse changeStatus(String cycleId, String target) {
        UserAccount user = campusAccessService.currentUser();
        requireOfficer(user);
        PlacementCycle cycle = loadAccessible(cycleId, user);

        String normalized = target == null ? "" : target.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown cycle status: " + target);
        }
        String current = cycle.getStatus() == null ? "PLANNING" : cycle.getStatus().toUpperCase();
        if (current.equals(normalized)) {
            return PlacementCycleResponse.from(cycle);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(normalized)) {
            throw new InvalidCycleTransitionException(
                "Cycle " + cycleId + " cannot move from " + current + " to " + normalized);
        }
        cycle.setStatus(normalized);
        return PlacementCycleResponse.from(placementCycleRepository.save(cycle));
    }

    private PlacementCycle loadAccessible(String cycleId, UserAccount user) {
        PlacementCycle cycle = placementCycleRepository.findById(cycleId)
            .orElseThrow(() -> new PlacementCycleNotFoundException(cycleId));
        campusAccessService.requireAccess(user, cycle.getCampusId());
        return cycle;
    }

    private void requireOfficer(UserAccount user) {
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage placement cycles");
        }
    }
}
