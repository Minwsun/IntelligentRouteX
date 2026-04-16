package com.routechain.v2.integration;

import com.routechain.domain.GeoPoint;

import java.time.Instant;

public interface OpenMeteoClient {
    OpenMeteoSnapshot fetchForecast(GeoPoint point, Instant decisionTime);

    OpenMeteoSnapshot fetchHistorical(GeoPoint point, Instant decisionTime);
}

