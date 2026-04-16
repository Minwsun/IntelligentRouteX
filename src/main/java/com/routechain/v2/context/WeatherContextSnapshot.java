package com.routechain.v2.context;

import com.routechain.v2.SchemaVersioned;

public record WeatherContextSnapshot(
        String schemaVersion,
        double multiplier,
        boolean weatherBadSignal,
        WeatherSource source,
        long sourceAgeMs,
        double confidence) implements SchemaVersioned {
}

