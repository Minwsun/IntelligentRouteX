package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.cluster.EtaLegCacheFactory;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.EtaFeatureBuilder;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.EtaUncertaintyEstimator;
import com.routechain.v2.context.TrafficProfileService;
import com.routechain.v2.context.WeatherContextService;
import com.routechain.v2.integration.NoOpOpenMeteoClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.NoOpTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverRouteFeatureBuilderTest {

    @Test
    void buildsStableFeaturesForBundleAnchorDriver() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        String bundleId = context.bundleIds().getFirst();
        PickupAnchor anchor = new PickupAnchor("pickup-anchor/v1", bundleId, context.orderSetSignature(bundleId), context.bundle(bundleId).seedOrderId(), 1, 0.8, List.of());
        DriverRouteFeatureBuilder builder = new DriverRouteFeatureBuilder();
        EtaService etaService = new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        var etaLegCache = new EtaLegCacheFactory(properties, etaService)
                .create(RouteTestFixtures.request().traceId(), RouteTestFixtures.request().decisionTime(), RouteTestFixtures.request().weatherProfile());

        DriverRouteFeatures features = builder.build(
                context.availableDrivers().getFirst(),
                anchor,
                context,
                RouteTestFixtures.etaContext(),
                etaLegCache);

        assertTrue(features.pickupEtaMinutes() >= 0.0);
        assertTrue(features.driverFitScore() >= 0.0);
        assertTrue(features.bundleScore() >= 0.0);
    }
}
