package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.TestTabularScoringClient;
import com.routechain.v2.route.DriverFitFeatureVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverFitMlBoundedScopeTest {

    @Test
    void workerIsCalledOnlyForBoundedShortlistMembership() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setMlEnabled(true);
        properties.getMl().getTabular().setEnabled(true);
        properties.getCandidate().setMaxDrivers(2);
        TestTabularScoringClient client = TestTabularScoringClient.applied(0.05);
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        PickupAnchor anchor = new PickupAnchor("pickup-anchor/v1", context.bundleIds().getFirst(), context.orderSetSignature(context.bundleIds().getFirst()), context.bundle(context.bundleIds().getFirst()).seedOrderId(), 1, 0.8, java.util.List.of());
        var etaLegCache = new com.routechain.v2.cluster.EtaLegCacheFactory(
                properties,
                new com.routechain.v2.context.EtaService(
                        properties,
                        new com.routechain.v2.context.BaselineTravelTimeEstimator(),
                        new com.routechain.v2.context.TrafficProfileService(properties),
                        new com.routechain.v2.context.WeatherContextService(properties, new com.routechain.v2.integration.NoOpOpenMeteoClient()),
                        new com.routechain.v2.integration.NoOpTomTomTrafficRefineClient(),
                        client,
                        new com.routechain.v2.context.EtaFeatureBuilder(),
                        new com.routechain.v2.context.EtaUncertaintyEstimator()))
                .create(RouteTestFixtures.request().traceId(), RouteTestFixtures.request().decisionTime(), RouteTestFixtures.request().weatherProfile());

        DriverShortlistResult result = new CandidateDriverShortlister(properties, new DriverRouteFeatureBuilder(), client)
                .shortlist(RouteTestFixtures.request().traceId(), context.availableDrivers(), anchor, context, RouteTestFixtures.etaContext(), etaLegCache);

        assertEquals(2, result.shortlistedFeatures().size());
        assertEquals(2, client.invocations().stream().filter(DriverFitFeatureVector.class::isInstance).count());
    }
}
