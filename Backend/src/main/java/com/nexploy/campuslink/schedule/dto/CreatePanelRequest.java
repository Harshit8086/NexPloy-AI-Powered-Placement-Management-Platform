package com.nexploy.campuslink.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request to create an interview panel. Name required; campusId optional (officers/admins may
 * target any campus, else the caller's campus). members is optional — each member carries a name
 * and email. id (panel and members) is server-assigned. New panels start active.
 */
public record CreatePanelRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String campusId,
    @Valid List<PanelMemberInput> members
) {

    public record PanelMemberInput(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 320) String email
    ) {
    }
}
