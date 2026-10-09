package com.nexploy.campuslink.placement.dto;

import com.nexploy.campuslink.placement.entity.PlacementCycle;
import java.time.LocalDate;

/** Frontend-facing view of a placement cycle (camelCase). */
public record PlacementCycleResponse(
    String id,
    String campusId,
    String name,
    String academicYear,
    LocalDate startsOn,
    LocalDate endsOn,
    String status
) {

    public static PlacementCycleResponse from(PlacementCycle cycle) {
        return new PlacementCycleResponse(
            cycle.getId(),
            cycle.getCampusId(),
            cycle.getName(),
            cycle.getAcademicYear(),
            cycle.getStartsOn(),
            cycle.getEndsOn(),
            cycle.getStatus()
        );
    }
}
