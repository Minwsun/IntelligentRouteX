package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaServiceLiveSourceIntegrationTest {

    @Test
    void tomtomRefineAppliesOnlyWhenPolicyAndBudgetAllow() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);
        properties.getWeather().setEnabled(true);

        EtaService service = buildService(properties, TestOpenMeteoClient.freshHeavyRain(), TestTomTomTrafficRefineClient.applied(1.15, true));
        EtaEstimate refined = service.estimate(request());
        properties.getTraffic().setRefineBudgetPerTick(0);
        EtaService budgetBlockedService = buildService(properties, TestOpenMeteoClient.freshHeavyRain(), TestTomTomTrafficRefineClient.applied(1.15, true));
        EtaEstimate deterministic = budgetBlockedService.estimate(request());

        assertTrue(refined.etaMinutes() > deterministic.etaMinutes());
        assertTrue("tomtom".equals(refined.refineSource()));
        assertTrue(deterministic.degradeReasons().contains("tomtom-budget-or-policy-skipped"));
    }

    @Test
    void bothLiveSourcesDownPreserveDeterministicPathWithDegradeReasons() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);
        properties.getWeather().setEnabled(true);

        EtaEstimate estimate = buildService(
                properties,
                TestOpenMeteoClient.unavailable("open-meteo-unavailable"),
                TestTomTomTrafficRefineClient.unavailable("tomtom-unavailable"))
                .estimate(request());

        assertTrue(estimate.etaMinutes() > 0.0);
        assertTrue(estimate.degradeReasons().contains("tomtom-unavailable"));
    }

    private EtaService buildService(RouteChainDispatchV2Properties properties,
                                    com.routechain.v2.integration.OpenMeteoClient openMeteoClient,
                                    com.routechain.v2.integration.TomTomTrafficRefineClient tomTomTrafficRefineClient) {
        return new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, openMeteoClient),
                tomTomTrafficRefineClient,
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
    }

    private EtaEstimateRequest request() {
        return new EtaEstimateRequest("eta-estimate-request/v1", "trace-eta-live", new GeoPoint(10.770, 106.690), new GeoPoint(10.780, 106.700), Instant.parse("2026-04-16T08:00:00Z"), WeatherProfile.CLEAR, "eta/context", 150L);
    }
}
