package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficProfileServiceTest {

    @Test
    void resolvesPeakProfile() {
        TrafficProfileService service = new TrafficProfileService(RouteChainDispatchV2Properties.defaults());
        TrafficProfileSnapshot snapshot = service.resolveTraffic(requestAt("2026-04-16T08:00:00Z"), point(), point2());
        assertEquals(TrafficProfileSource.PROFILE_PEAK, snapshot.source());
        assertTrue(snapshot.trafficBadSignal());
    }

    @Test
    void resolvesOffPeakProfile() {
        TrafficProfileService service = new TrafficProfileService(RouteChainDispatchV2Properties.defaults());
        TrafficProfileSnapshot snapshot = service.resolveTraffic(requestAt("2026-04-16T23:00:00Z"), point(), point2());
        assertEquals(TrafficProfileSource.PROFILE_OFFPEAK, snapshot.source());
        assertFalse(snapshot.trafficBadSignal());
    }

    @Test
    void fallsBackToDegradedProfileWhenMissingPoints() {
        TrafficProfileService service = new TrafficProfileService(RouteChainDispatchV2Properties.defaults());
        TrafficProfileSnapshot snapshot = service.resolveTraffic(requestAt("2026-04-16T12:00:00Z"), null, point2());
        assertEquals(TrafficProfileSource.DEGRADED_PROFILE, snapshot.source());
    }

    private DispatchV2Request requestAt(String instant) {
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-traffic",
                List.of(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.parse(instant));
    }

    private GeoPoint point() {
        return new GeoPoint(10.770, 106.690);
    }

    private GeoPoint point2() {
        return new GeoPoint(10.780, 106.700);
    }
}

