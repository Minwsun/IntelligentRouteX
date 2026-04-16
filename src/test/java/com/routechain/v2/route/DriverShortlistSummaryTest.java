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

import static org.junit.jupiter.api.Assertions.assertEquals;

class DriverShortlistSummaryTest {

    @Test
    void rawShortlistCountAndRerankedRetainedCountAreTrackedSeparately() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchRouteCandidateService service = new DispatchRouteCandidateService(
                new PickupAnchorSelector(properties),
                new CandidateDriverShortlister(properties, new DriverRouteFeatureBuilder()),
                new DriverReranker(),
                new EtaLegCacheFactory(
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

        DriverShortlistSummary summary = service.summarizeDrivers(2, 3, 6, 4, List.of("test"));

        assertEquals(6, summary.shortlistedDriverCount());
        assertEquals(4, summary.rerankedDriverCount());
    }
}
