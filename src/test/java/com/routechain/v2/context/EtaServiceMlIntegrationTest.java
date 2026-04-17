package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import com.routechain.v2.integration.TestTabularScoringClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaServiceMlIntegrationTest {

    @Test
    void workerUpAppliesResidualAndWorkerDownPreservesDeterministicEta() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);

        EtaEstimate deterministic = buildService(properties, TestTabularScoringClient.notApplied("tabular-unavailable"))
                .estimate(request());
        EtaEstimate adjusted = buildService(properties, TestTabularScoringClient.applied(1.5))
                .estimate(request());

        assertTrue(adjusted.etaMinutes() > deterministic.etaMinutes());
        assertTrue(deterministic.degradeReasons().contains("eta-ml-unavailable"));
    }

    private EtaService buildService(RouteChainDispatchV2Properties properties,
                                    com.routechain.v2.integration.TabularScoringClient client) {
        return new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                client,
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
    }

    private EtaEstimateRequest request() {
        return new EtaEstimateRequest(
                "eta-estimate-request/v1",
                "trace-eta-ml",
                new GeoPoint(10.770, 106.690),
                new GeoPoint(10.780, 106.700),
                Instant.parse("2026-04-16T12:00:00Z"),
                WeatherProfile.CLEAR,
                "eta/context",
                150L);
    }
}
