package com.nexploy.campuslink.campus.dto;

import com.nexploy.campuslink.campus.entity.Campus;

public record CampusResponse(
    String id,
    String name,
    String city,
    String state,
    boolean active
) {

    public static CampusResponse from(Campus campus) {
        return new CampusResponse(
            campus.getId(),
            campus.getName(),
            campus.getCity(),
            campus.getState(),
            campus.isActive()
        );
    }
}
