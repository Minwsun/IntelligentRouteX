package com.routechain.v2.context;

import com.routechain.v2.SchemaVersioned;

public record FreshnessMetadata(
        String schemaVersion,
        long weatherAgeMs,
        long trafficAgeMs,
        long forecastAgeMs,
        boolean weatherFresh,
        boolean trafficFresh,
        boolean forecastFresh) implements SchemaVersioned {

    public static FreshnessMetadata empty() {
        return new FreshnessMetadata(
                "freshness-metadata/v1",
                0L,
                0L,
                0L,
                false,
                false,
                false);
    }
}

