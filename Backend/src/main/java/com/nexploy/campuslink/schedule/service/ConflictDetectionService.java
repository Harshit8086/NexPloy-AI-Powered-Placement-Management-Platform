package com.nexploy.campuslink.schedule.service;

import com.nexploy.campuslink.schedule.dto.ScheduleConflictResponse;
import com.nexploy.campuslink.schedule.entity.BlockedSlot;
import com.nexploy.campuslink.schedule.entity.ConflictType;
import com.nexploy.campuslink.schedule.entity.EventAssignment;
import com.nexploy.campuslink.schedule.entity.PanelMember;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import com.nexploy.campuslink.schedule.entity.Venue;
import com.nexploy.campuslink.schedule.repository.BlockedSlotRepository;
import com.nexploy.campuslink.schedule.repository.EventAssignmentRepository;
import com.nexploy.campuslink.schedule.repository.PanelMemberRepository;
import com.nexploy.campuslink.schedule.repository.ScheduleEventRepository;
import com.nexploy.campuslink.schedule.repository.VenueRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConflictDetectionService {

    private final ScheduleEventRepository scheduleEventRepository;
    private final VenueRepository venueRepository;
    private final BlockedSlotRepository blockedSlotRepository;
    private final PanelMemberRepository panelMemberRepository;
    private final EventAssignmentRepository eventAssignmentRepository;

    public List<ScheduleConflictResponse> detect(String campusId) {
        List<ScheduleEvent> events = campusId == null
            ? scheduleEventRepository.findAll()
            : scheduleEventRepository.findByCampusId(campusId);
        Map<String, Integer> venueCapacityById = venueCapacities(campusId);
        Map<String, Set<String>> membersByPanel = panelMembers();
        Map<String, Set<String>> studentsByEvent = studentsByEvent(campusId);
        List<BlockedSlot> blockedSlots = campusId == null
            ? blockedSlotRepository.findAll()
            : blockedSlotRepository.findByCampusId(campusId);
        List<ScheduleConflictResponse> conflicts = new ArrayList<>();

        for (int index = 0; index < events.size(); index++) {
            ScheduleEvent event = events.get(index);
            Integer capacity = resolveCapacity(event, venueCapacityById);
            if (event.getExpectedParticipants() != null
                && capacity != null
                && event.getExpectedParticipants() > capacity) {
                conflicts.add(ScheduleConflictResponse.capacity(event, capacity));
            }
            for (BlockedSlot slot : blockedSlots) {
                if (applies(slot, event)
                    && overlaps(event.getStartsAt(), event.getEndsAt(), slot.getStartsAt(), slot.getEndsAt())) {
                    conflicts.add(ScheduleConflictResponse.blockedSlot(event, slot.getReason(),
                        latestStart(event.getStartsAt(), slot.getStartsAt()),
                        earliestEnd(event.getEndsAt(), slot.getEndsAt())));
                }
            }
            for (int nextIndex = index + 1; nextIndex < events.size(); nextIndex++) {
                ScheduleEvent other = events.get(nextIndex);
                if (!overlaps(event.getStartsAt(), event.getEndsAt(), other.getStartsAt(), other.getEndsAt())) {
                    continue;
                }
                OffsetDateTime overlapStart = latestStart(event.getStartsAt(), other.getStartsAt());
                OffsetDateTime overlapEnd = earliestEnd(event.getEndsAt(), other.getEndsAt());
                String sharedStudent = sharedMember(studentsByEvent, event.getId(), other.getId());
                if (sharedStudent != null) {
                    conflicts.add(ScheduleConflictResponse.overlap(
                        ConflictType.STUDENT_OVERLAP, event, other, sharedStudent, overlapStart, overlapEnd));
                }
                if (same(event.getVenueId(), other.getVenueId())) {
                    conflicts.add(ScheduleConflictResponse.overlap(
                        ConflictType.VENUE_OVERLAP, event, other, event.getVenueId(), overlapStart, overlapEnd));
                }
                if (same(event.getPanelId(), other.getPanelId())) {
                    conflicts.add(ScheduleConflictResponse.overlap(
                        ConflictType.PANEL_OVERLAP, event, other, event.getPanelId(), overlapStart, overlapEnd));
                } else {
                    String sharedMember = sharedMember(membersByPanel, event.getPanelId(), other.getPanelId());
                    if (sharedMember != null) {
                        conflicts.add(ScheduleConflictResponse.overlap(
                            ConflictType.PANEL_MEMBER_OVERLAP, event, other, sharedMember, overlapStart, overlapEnd));
                    }
                }
            }
        }
        return conflicts;
    }

    private Map<String, Integer> venueCapacities(String campusId) {
        List<Venue> venues = campusId == null
            ? venueRepository.findAll()
            : venueRepository.findByCampusId(campusId);
        Map<String, Integer> byId = new HashMap<>();
        for (Venue venue : venues) {
            byId.put(venue.getId(), venue.getCapacity());
        }
        return byId;
    }

    private Map<String, Set<String>> panelMembers() {
        Map<String, Set<String>> byPanel = new HashMap<>();
        for (PanelMember member : panelMemberRepository.findAll()) {
            byPanel.computeIfAbsent(member.getPanelId(), key -> new HashSet<>()).add(member.getId());
        }
        return byPanel;
    }

    /** Candidate assignments grouped by event id, so two overlapping events can be checked for a shared student. */
    private Map<String, Set<String>> studentsByEvent(String campusId) {
        List<EventAssignment> assignments = campusId == null
            ? eventAssignmentRepository.findAll()
            : eventAssignmentRepository.findByCampusId(campusId);
        Map<String, Set<String>> byEvent = new HashMap<>();
        for (EventAssignment assignment : assignments) {
            byEvent.computeIfAbsent(assignment.getEventId(), key -> new HashSet<>())
                .add(assignment.getStudentId());
        }
        return byEvent;
    }

    private Integer resolveCapacity(ScheduleEvent event, Map<String, Integer> venueCapacityById) {
        Integer fromVenue = event.getVenueId() == null ? null : venueCapacityById.get(event.getVenueId());
        return fromVenue != null ? fromVenue : event.getVenueCapacity();
    }

    private boolean applies(BlockedSlot slot, ScheduleEvent event) {
        if (slot.getCampusId() != null && !slot.getCampusId().equals(event.getCampusId())) {
            return false;
        }
        return slot.getVenueId() == null || slot.getVenueId().equals(event.getVenueId());
    }

    private String sharedMember(Map<String, Set<String>> membersByPanel, String firstPanel, String secondPanel) {
        if (firstPanel == null || secondPanel == null) {
            return null;
        }
        Set<String> first = membersByPanel.get(firstPanel);
        Set<String> second = membersByPanel.get(secondPanel);
        if (first == null || second == null) {
            return null;
        }
        for (String member : first) {
            if (second.contains(member)) {
                return member;
            }
        }
        return null;
    }

    private boolean overlaps(OffsetDateTime firstStart, OffsetDateTime firstEnd,
                             OffsetDateTime secondStart, OffsetDateTime secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private boolean same(String first, String second) {
        return first != null && first.equals(second);
    }

    private OffsetDateTime latestStart(OffsetDateTime first, OffsetDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private OffsetDateTime earliestEnd(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }
}
