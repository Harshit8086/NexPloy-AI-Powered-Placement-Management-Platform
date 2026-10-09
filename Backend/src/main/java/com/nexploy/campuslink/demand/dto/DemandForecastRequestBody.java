package com.nexploy.campuslink.demand.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * Historical demand series to forecast for a target cycle. Cycle-scoped and standalone: the
 * forecast informs training plans and analytics only, carries no student identity, and never
 * drives exclusion. History points must be supplied in chronological order.
 */
public record DemandForecastRequestBody(
    @NotBlank String targetCycleId,
    @NotEmpty @Valid List<Series> series
) {

    public record Series(
        @NotBlank String key,
        @NotBlank String dimension,
        @NotEmpty @Valid List<Point> history
    ) {
    }

    public record Point(
        @NotBlank String placementCycleId,
        @NotNull @PositiveOrZero Double count
    ) {
    }
}
