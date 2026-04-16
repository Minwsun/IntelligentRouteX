package com.routechain.v2.context;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.SchemaVersioned;

import java.time.Instant;

public record EtaEstimateRequest(
        String schemaVersion,
        String traceId,
        GeoPoint from,
        GeoPoint to,
        Instant decisionTime,
        WeatherProfile weatherProfile,
        String stageName,
        long timeoutBudgetMs) implements SchemaVersioned {
}

