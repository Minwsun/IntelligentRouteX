package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.integration.TestOpenMeteoClient;
import com.routechain.v2.integration.TestTomTomTrafficRefineClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayLiveSourceMetadataMismatchTest {

    @Test
    void replayReportsExplicitLiveSourceMetadataMismatchReasons() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getWeather().setEnabled(true);
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);

        DispatchV2Request request = liveTrafficEligibleRequest();
        DispatchV2Result referenceResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestOpenMeteoClient.freshHeavyRain(),
                TestTomTomTrafficRefineClient.applied(1.15, true))
                .dispatch(request);
        DecisionLogRecord reference = new DecisionLogAssembler().assemble(request, referenceResult);

        DispatchV2Result replayResult = TestDispatchV2Factory.core(
                properties,
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                TestOpenMeteoClient.staleHeavyRain(60_000L),
                TestTomTomTrafficRefineClient.stale(1.15, 60_000L))
                .dispatch(request);

        ReplayComparisonResult comparisonResult = new DispatchReplayComparator().compare(reference, null, replayResult);

        assertTrue(comparisonResult.mismatchReasons().contains("live-weather-age-mismatch"));
        assertTrue(comparisonResult.mismatchReasons().contains("live-traffic-age-mismatch"));
    }

    private DispatchV2Request liveTrafficEligibleRequest() {
        Instant decisionTime = Instant.parse("2026-04-16T12:00:00Z");
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-live-replay",
                List.of(new Order("order-live", new GeoPoint(10.81, 106.75), new GeoPoint(10.83, 106.78), decisionTime.minusSeconds(300), decisionTime, 20, false)),
                List.of(new Driver("driver-live", new GeoPoint(10.74, 106.67))),
                List.of(),
                WeatherProfile.CLEAR,
                decisionTime);
    }
}
