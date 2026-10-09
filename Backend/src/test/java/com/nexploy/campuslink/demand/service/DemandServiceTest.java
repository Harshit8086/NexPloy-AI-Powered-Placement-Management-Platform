package com.nexploy.campuslink.demand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.demand.client.MlDemandClient;
import com.nexploy.campuslink.demand.client.MlDemandClient.DemandForecastRequest;
import com.nexploy.campuslink.demand.client.MlDemandClient.DemandForecastResult;
import com.nexploy.campuslink.demand.dto.DemandForecastRequestBody;
import com.nexploy.campuslink.demand.dto.DemandForecastRequestBody.Point;
import com.nexploy.campuslink.demand.dto.DemandForecastRequestBody.Series;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class DemandServiceTest {

    private final CampusAccessService campusAccessService = new CampusAccessService();
    private final MlDemandClient mlDemandClient = org.mockito.Mockito.mock(MlDemandClient.class);
    private final DemandService service = new DemandService(campusAccessService, mlDemandClient);

    DemandServiceTest() {
        ReflectionTestUtils.setField(service, "modelPolicyVersion", "policy-test");
    }

    @Test
    void forwardsSeriesAndTargetCycleForAnAccessibleCampus() {
        authenticate(user(UserRole.OFFICER, "campus_demo"));
        when(mlDemandClient.forecast(org.mockito.ArgumentMatchers.any())).thenReturn(sample());

        service.forecast("campus_demo", "cycle-2026", new DemandForecastRequestBody("2027", List.of(
            new Series("python", "SKILL", List.of(
                new Point("2024", 10.0), new Point("2025", 20.0), new Point("2026", 30.0)))
        )));

        ArgumentCaptor<DemandForecastRequest> captor = ArgumentCaptor.forClass(DemandForecastRequest.class);
        org.mockito.Mockito.verify(mlDemandClient).forecast(captor.capture());
        DemandForecastRequest sent = captor.getValue();
        assertThat(sent.campusId()).isEqualTo("campus_demo");
        assertThat(sent.modelPolicyVersion()).isEqualTo("policy-test");
        assertThat(sent.targetCycleId()).isEqualTo("2027");
        assertThat(sent.series()).hasSize(1);
        assertThat(sent.series().get(0).key()).isEqualTo("python");
        assertThat(sent.series().get(0).history()).hasSize(3);
    }

    @Test
    void recruiterCannotForecastForAnotherCampus() {
        authenticate(user(UserRole.RECRUITER, "campus_demo"));

        assertThatThrownBy(() -> service.forecast("campus_other", "current",
            new DemandForecastRequestBody("2027", List.of(
                new Series("cse", "BRANCH", List.of(new Point("2025", 40.0)))))))
            .isInstanceOf(AccessDeniedException.class);
    }

    private DemandForecastResult sample() {
        return new DemandForecastResult("2027", List.of(), "demand-baseline-2026-01",
            "cycle-features-1", OffsetDateTime.parse("2026-09-20T12:00:00Z"));
    }

    private UserAccount user(UserRole role, String campusId) {
        return UserAccount.builder().username("user-" + role).password("{noop}s")
            .campusId(campusId).role(role).enabled(true).build();
    }

    private void authenticate(UserAccount user) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
