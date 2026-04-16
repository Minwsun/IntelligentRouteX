package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.EtaEstimate;
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

import static org.junit.jupiter.api.Assertions.assertSame;

class EtaLegCacheTest {

    @Test
    void cachesByExplicitContextAndEndpointsWithoutSyntheticRequest() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        EtaService etaService = new EtaService(
                properties,
                new BaselineTravelTimeEstimator(),
                new TrafficProfileService(properties),
                new WeatherContextService(properties, new NoOpOpenMeteoClient()),
                new NoOpTomTomTrafficRefineClient(),
                new NoOpTabularScoringClient(),
                new EtaFeatureBuilder(),
                new EtaUncertaintyEstimator());
        EtaLegCache cache = new EtaLegCache(
                etaService,
                "trace-cache",
                Instant.parse("2026-04-16T12:00:00Z"),
                WeatherProfile.CLEAR,
                120L);

        EtaEstimate first = cache.getOrEstimate(new GeoPoint(10.775, 106.700), new GeoPoint(10.776, 106.701), "pair-graph", "a->b");
        EtaEstimate second = cache.getOrEstimate(new GeoPoint(10.775, 106.700), new GeoPoint(10.776, 106.701), "pair-graph", "a->b");

        assertSame(first, second);
    }
}
