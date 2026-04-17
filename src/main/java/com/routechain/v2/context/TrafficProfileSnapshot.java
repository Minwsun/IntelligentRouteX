package com.routechain.v2.context;

import com.routechain.v2.SchemaVersioned;

public record TrafficProfileSnapshot(
        String schemaVersion,
        double multiplier,
        TrafficProfileSource source,
        long sourceAgeMs,
        double confidence,
        boolean trafficBadSignal,
        String degradeReason) implements SchemaVersioned {
}
