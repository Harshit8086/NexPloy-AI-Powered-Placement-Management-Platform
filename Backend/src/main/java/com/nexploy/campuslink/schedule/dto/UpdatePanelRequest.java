package com.nexploy.campuslink.schedule.dto;

import com.nexploy.campuslink.schedule.dto.CreatePanelRequest.PanelMemberInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request to replace a panel's editable fields (PUT). When members is non-null the panel's member
 * roster is replaced wholesale; a null members list leaves the existing roster untouched. active
 * moves via PATCH /active.
 */
public record UpdatePanelRequest(
    @NotBlank @Size(max = 200) String name,
    @Valid List<PanelMemberInput> members
) {
}
