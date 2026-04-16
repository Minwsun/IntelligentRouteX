package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.cluster.EtaLegCache;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateDriverShortlisterTest {

    @Test
    void shortlistsOnlyAvailableDriversAndRespectsMaxDrivers() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getCandidate().setMaxDrivers(2);
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        PickupAnchor anchor = new PickupAnchor("pickup-anchor/v1", context.bundleIds().getFirst(), context.orderSetSignature(context.bundleIds().getFirst()), context.bundle(context.bundleIds().getFirst()).seedOrderId(), 1, 0.8, List.of());
        EtaLegCache etaLegCache = new EtaLegCacheFactory(
                properties,
                new EtaService(
                        properties,
                        new BaselineTravelTimeEstimator(),
                        new TrafficProfileService(properties),
                        new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                        new NoOpTomTomTrafficRefineClient(),
                        new NoOpTabularScoringClient(),
                        new EtaFeatureBuilder(),
                        new EtaUncertaintyEstimator()))
                .create(RouteTestFixtures.request().traceId(), RouteTestFixtures.request().decisionTime(), RouteTestFixtures.request().weatherProfile());
        CandidateDriverShortlister shortlister = new CandidateDriverShortlister(properties, new DriverRouteFeatureBuilder());

        List<DriverRouteFeatures> shortlisted = shortlister.shortlist(
                context.availableDrivers(),
                anchor,
                context,
                RouteTestFixtures.etaContext(),
                etaLegCache);

        assertEquals(2, shortlisted.size());
        assertTrue(shortlisted.stream().allMatch(feature -> context.availableDrivers().stream().anyMatch(driver -> driver.driverId().equals(feature.driverId()))));
    }
}
