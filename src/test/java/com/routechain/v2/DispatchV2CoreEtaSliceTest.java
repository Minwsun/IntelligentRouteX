package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreEtaSliceTest {

    @Test
    void returnsEtaStageOnlyForEnabledCore() {
        DispatchV2Core core = buildCore(RouteChainDispatchV2Properties.defaults());
        DispatchV2Result result = core.dispatch(sampleRequest());
        assertEquals(List.of("eta/context"), result.decisionStages());
        assertFalse(result.fallbackUsed());
        assertNull(result.selectedRouteId());
        assertNotNull(result.etaContext());
        assertNotNull(result.etaStageTrace());
        assertNotNull(result.freshnessMetadata());
    }

    @Test
    void emptySamplingPolicyReturnsValidContextWithDegradeReason() {
        DispatchV2Core core = buildCore(RouteChainDispatchV2Properties.defaults());
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-empty",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertEquals(List.of("eta/context"), result.decisionStages());
        assertEquals(0, result.etaContext().sampledLegCount());
        assertTrue(result.degradeReasons().contains("no-sampleable-eta-leg"));
    }

    private DispatchV2Core buildCore(RouteChainDispatchV2Properties properties) {
        return new DispatchV2Core(new DispatchEtaContextService(
                properties,
                new EtaService(
                        properties,
                        new BaselineTravelTimeEstimator(),
                        new TrafficProfileService(properties),
                        new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                        new NoOpTomTomTrafficRefineClient(),
                        new NoOpTabularScoringClient(),
                        new EtaFeatureBuilder(),
                        new EtaUncertaintyEstimator())));
    }

    private DispatchV2Request sampleRequest() {
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-core",
                List.of(new Order(
                        "order-1",
                        new GeoPoint(10.775, 106.700),
                        new GeoPoint(10.780, 106.710),
                        Instant.now(),
                        Instant.now(),
                        20,
                        false)),
                List.of(new Driver("driver-1", new GeoPoint(10.770, 106.695))),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now());
    }
}
