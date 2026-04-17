package com.routechain.v2.integration;

import com.routechain.domain.GeoPoint;

import java.time.Instant;
import java.util.function.BiFunction;

public final class TestOpenMeteoClient implements OpenMeteoClient {
    private final BiFunction<GeoPoint, Instant, OpenMeteoSnapshot> forecastFunction;

    public TestOpenMeteoClient(BiFunction<GeoPoint, Instant, OpenMeteoSnapshot> forecastFunction) {
        this.forecastFunction = forecastFunction;
    }

    public static TestOpenMeteoClient freshHeavyRain() {
        return new TestOpenMeteoClient((point, decisionTime) -> new OpenMeteoSnapshot(true, "heavy-rain", 1.28, true, 0L, 0.92, 5L, ""));
    }

    public static TestOpenMeteoClient staleHeavyRain(long sourceAgeMs) {
        return new TestOpenMeteoClient((point, decisionTime) -> new OpenMeteoSnapshot(true, "heavy-rain", 1.28, true, sourceAgeMs, 0.92, 5L, ""));
    }

    public static TestOpenMeteoClient unavailable(String degradeReason) {
        return new TestOpenMeteoClient((point, decisionTime) -> new OpenMeteoSnapshot(false, "unknown", 1.0, false, Long.MAX_VALUE, 0.0, 5L, degradeReason));
    }

    @Override
    public OpenMeteoSnapshot fetchForecast(GeoPoint point, Instant decisionTime) {
        return forecastFunction.apply(point, decisionTime);
    }

    @Override
    public OpenMeteoSnapshot fetchHistorical(GeoPoint point, Instant decisionTime) {
        return forecastFunction.apply(point, decisionTime);
    }
}
