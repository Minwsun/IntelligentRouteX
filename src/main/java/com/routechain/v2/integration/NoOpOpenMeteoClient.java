package com.routechain.v2.integration;

import com.routechain.domain.GeoPoint;

import java.time.Instant;

public final class NoOpOpenMeteoClient implements OpenMeteoClient {
    @Override
    public OpenMeteoSnapshot fetchForecast(GeoPoint point, Instant decisionTime) {
        return OpenMeteoSnapshot.unavailable();
    }

    @Override
    public OpenMeteoSnapshot fetchHistorical(GeoPoint point, Instant decisionTime) {
        return OpenMeteoSnapshot.unavailable();
    }
}
