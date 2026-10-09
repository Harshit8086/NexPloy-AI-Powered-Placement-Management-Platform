package com.nexploy.campuslink.demand.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Boundary to the Python ML inference service for demand forecasting. Forecasts inform training
 * plans and analytics only; they carry no student identity and never drive exclusion. Spring
 * remains the system of record and only requests a versioned, uncertainty-bounded forecast.
 */
public interface MlDemandClient {

    DemandForecastResult forecast(DemandForecastRequest request);

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record DemandForecastRequest(
        String requestId,
        String campusId,
        String placementCycleId,
        String modelPolicyVersion,
        String targetCycleId,
        List<Series> series
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Series(
        String key,
        String dimension,
        List<SeriesPoint> history
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SeriesPoint(
        String placementCycleId,
        double count
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record DemandForecastResult(
        String targetCycleId,
        List<Forecast> forecasts,
        String modelVersion,
        String featureVersion,
        OffsetDateTime generatedAt
    ) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Forecast(
        String key,
        String dimension,
        Double forecast,
        Double lower,
        Double upper,
        String trend,
        double confidence,
        List<String> warnings
    ) {
    }
}
