package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NeuralRoutePriorClientTest {

    @Test
    void shouldUseFreshCacheWhenRemoteUnavailable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-31T00:00:00Z"));
        AtomicInteger fetchCalls = new AtomicInteger();
        NeuralRoutePriorClient client = new NeuralRoutePriorClient(
                URI.create("http://127.0.0.1:8094/prior"),
                Duration.ofSeconds(25),
                Duration.ofMillis(20),
                true,
                clock,
                request -> {
                    if (fetchCalls.incrementAndGet() == 1) {
                        return NeuralRoutePriorClient.FetchResult.success(new NeuralRoutePrior(
                                request.zoneId(),
                                0.76,
                                List.of("r-1", "r-2"),
                                0.88,
                                0,
                                "routefinder-v1",
                                true,
                                false,
                                "none",
                                "neural-route-prior",
                                "python-sidecar",
                                12,
                                clock.instant()
                        ));
                    }
                    return NeuralRoutePriorClient.FetchResult.failure("timeout");
                }
        );

        DispatchPlan plan = samplePlan();
        NeuralRoutePrior first = client.resolve("RUN-s42-0001", "MAINLINE_REALISTIC", null, plan, WeatherProfile.CLEAR, 0.35);
        NeuralRoutePrior second = client.resolve("RUN-s42-0001", "MAINLINE_REALISTIC", null, plan, WeatherProfile.CLEAR, 0.35);

        assertTrue(first.used());
        assertTrue(second.used());
        assertEquals("routefinder-v1", second.modelVersion());
        assertEquals(1, fetchCalls.get(), "fresh cache should avoid second remote call");
    }

    @Test
    void shouldFallbackWhenCacheStaleAndRemoteUnavailable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-31T00:00:00Z"));
        AtomicInteger fetchCalls = new AtomicInteger();
        NeuralRoutePriorClient client = new NeuralRoutePriorClient(
                URI.create("http://127.0.0.1:8094/prior"),
                Duration.ofSeconds(10),
                Duration.ofMillis(20),
                true,
                clock,
                request -> {
                    if (fetchCalls.incrementAndGet() == 1) {
                        return NeuralRoutePriorClient.FetchResult.success(new NeuralRoutePrior(
                                request.zoneId(),
                                0.64,
                                List.of("r-9"),
                                0.82,
                                0,
                                "routefinder-v1",
                                true,
                                false,
                                "none",
                                "neural-route-prior",
                                "python-sidecar",
                                11,
                                clock.instant()
                        ));
                    }
                    return NeuralRoutePriorClient.FetchResult.failure("timeout");
                }
        );

        DispatchPlan plan = samplePlan();
        NeuralRoutePrior fresh = client.resolve("RUN-s42-0001", "MAINLINE_REALISTIC", null, plan, WeatherProfile.CLEAR, 0.35);
        assertTrue(fresh.used());

        clock.setInstant(clock.instant().plusSeconds(20));
        NeuralRoutePrior staleFallback = client.resolve("RUN-s42-0001", "MAINLINE_REALISTIC", null, plan, WeatherProfile.CLEAR, 0.35);

        assertFalse(staleFallback.used());
        assertTrue(staleFallback.fallbackUsed());
        assertTrue(staleFallback.fallbackReason().contains("stale-timeout"));
        assertEquals(2, fetchCalls.get(), "stale cache should trigger re-fetch attempt");
    }

    private DispatchPlan samplePlan() {
        Driver driver = new Driver(
                "driver-1",
                "Driver 1",
                new GeoPoint(10.776, 106.700),
                "zone-a",
                VehicleType.MOTORBIKE
        );
        return new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("bundle-1", List.of(), 0.0, 0),
                List.of()
        );
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void setInstant(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
