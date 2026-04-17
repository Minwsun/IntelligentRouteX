package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaServiceTest {

    @Test
    void computesBaselineOnlyWhenMlAndTomTomDisabled() {
        EtaService service = buildService(RouteChainDispatchV2Properties.defaults());
        EtaEstimate estimate = service.estimate(request(WeatherProfile.CLEAR));
        assertTrue(estimate.etaMinutes() > 0.0);
        assertTrue(estimate.degradeReasons().contains("tomtom-disabled"));
        assertTrue(estimate.degradeReasons().contains("eta-ml-disabled"));
    }

    @Test
    void weatherAdjustmentChangesEta() {
        EtaService service = buildService(RouteChainDispatchV2Properties.defaults());
        EtaEstimate clear = service.estimate(request(WeatherProfile.CLEAR));
        EtaEstimate rain = service.estimate(request(WeatherProfile.HEAVY_RAIN));
        assertTrue(rain.etaMinutes() > clear.etaMinutes());
        assertTrue(rain.weatherBadSignal());
    }

    @Test
    void trafficPeakChangesEta() {
        EtaService service = buildService(RouteChainDispatchV2Properties.defaults());
        EtaEstimate peak = service.estimate(requestAt(WeatherProfile.CLEAR, "2026-04-16T08:00:00Z"));
        EtaEstimate defaultHour = service.estimate(requestAt(WeatherProfile.CLEAR, "2026-04-16T12:00:00Z"));
        assertTrue(peak.etaMinutes() > defaultHour.etaMinutes());
        assertTrue(peak.trafficBadSignal());
    }

    @Test
    void mlEnabledWithoutWorkerStillDispatchesWithDegradeReason() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        EtaService service = buildService(properties);
        EtaEstimate estimate = service.estimate(request(WeatherProfile.CLEAR));
        assertTrue(estimate.degradeReasons().contains("eta-ml-unavailable"));
        assertFalse(estimate.degradeReasons().contains("eta-ml-disabled"));
    }

    private EtaService buildService(RouteChainDispatchV2Properties properties) {
        return new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
    }

    private EtaEstimateRequest request(WeatherProfile weatherProfile) {
        return requestAt(weatherProfile, "2026-04-16T12:00:00Z");
    }

    private EtaEstimateRequest requestAt(WeatherProfile weatherProfile, String instant) {
        return new EtaEstimateRequest(
                "eta-estimate-request/v1",
                "trace-eta",
                new GeoPoint(10.770, 106.690),
                new GeoPoint(10.780, 106.700),
                Instant.parse(instant),
                weatherProfile,
                "eta/context",
                150L);
    }
}
