package com.nexploy.campuslink.schedule.dto;

import com.nexploy.campuslink.schedule.entity.Venue;

public record VenueResponse(
    String id,
    String campusId,
    String name,
    Integer capacity,
    boolean active
) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
            venue.getId(),
            venue.getCampusId(),
            venue.getName(),
            venue.getCapacity(),
            venue.isActive()
        );
    }
}
