package com.nexploy.campuslink.demand.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.demand.client.MlDemandClient;
import com.nexploy.campuslink.demand.client.MlDemandClient.DemandForecastRequest;
import com.nexploy.campuslink.demand.client.MlDemandClient.DemandForecastResult;
import com.nexploy.campuslink.demand.client.MlDemandClient.Series;
import com.nexploy.campuslink.demand.client.MlDemandClient.SeriesPoint;
import com.nexploy.campuslink.demand.dto.DemandForecastRequestBody;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates demand forecasting for a campus and target cycle. Spring enforces campus access
 * before forwarding the historical series to the stateless ML service. The forecast is
 * analytics/planning input only: it carries no student identity and never drives an exclusion
 * or ranking decision here.
 */
@Service
@RequiredArgsConstructor
public class DemandService {

    private final CampusAccessService campusAccessService;
    private final MlDemandClient mlDemandClient;

    @Value("${ml.policy-version:policy-2026-01}")
    private String modelPolicyVersion;

    public DemandForecastResult forecast(String campusId, String placementCycleId,
                                         DemandForecastRequestBody body) {
        UserAccount user = campusAccessService.currentUser();
        campusAccessService.requireAccess(user, campusId);

        List<Series> series = body.series().stream()
            .map(this::toSeries)
            .toList();

        DemandForecastRequest request = new DemandForecastRequest(
            UUID.randomUUID().toString(),
            campusId,
            placementCycleId,
            modelPolicyVersion,
            body.targetCycleId(),
            series
        );
        return mlDemandClient.forecast(request);
    }

    private Series toSeries(DemandForecastRequestBody.Series input) {
        List<SeriesPoint> points = input.history().stream()
            .map(point -> new SeriesPoint(point.placementCycleId(), point.count()))
            .toList();
        return new Series(input.key(), input.dimension(), points);
    }
}
