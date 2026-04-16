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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CompatibleCoreTest {

    @Test
    void fallsBackWhenDisabled() {
        DispatchV2CompatibleCore core = buildCore(RouteChainDispatchV2Properties.defaults());
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-1",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertTrue(result.fallbackUsed());
    }

    @Test
    void delegatesWhenEnabled() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        DispatchV2CompatibleCore core = buildCore(properties);
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-2",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.now()));
        assertFalse(result.fallbackUsed());
        assertTrue(result.decisionStages().contains("eta/context"));
    }

    @Test
    void dispatchesWhenMlEnabledButNoSidecarExists() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setEnabled(true);
        properties.setMlEnabled(true);
        DispatchV2CompatibleCore core = buildCore(properties);
        DispatchV2Result result = core.dispatch(new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-3",
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
                Instant.now()));
        assertFalse(result.fallbackUsed());
        assertTrue(result.degradeReasons().contains("eta-ml-unavailable-or-disabled-path"));
    }

    private DispatchV2CompatibleCore buildCore(RouteChainDispatchV2Properties properties) {
        DispatchV2Configuration configuration = new DispatchV2Configuration();
        BaselineTravelTimeEstimator baselineTravelTimeEstimator = configuration.baselineTravelTimeEstimator();
        TrafficProfileService trafficProfileService = configuration.trafficProfileService(properties);
        WeatherContextService weatherContextService = configuration.weatherContextService(properties, new NoOpOpenMeteoClient());
        EtaFeatureBuilder etaFeatureBuilder = configuration.etaFeatureBuilder();
        EtaUncertaintyEstimator etaUncertaintyEstimator = configuration.etaUncertaintyEstimator();
        EtaService etaService = configuration.etaService(
                properties,
                baselineTravelTimeEstimator,
                trafficProfileService,
                weatherContextService,
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                etaFeatureBuilder,
                etaUncertaintyEstimator);
        DispatchEtaContextService dispatchEtaContextService = configuration.dispatchEtaContextService(properties, etaService);
        DispatchV2Core dispatchV2Core = configuration.dispatchV2Core(dispatchEtaContextService);
        return configuration.dispatchV2CompatibleCore(properties, dispatchV2Core);
    }
}
