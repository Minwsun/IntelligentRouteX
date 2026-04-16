package com.routechain.v2.context;

import com.routechain.v2.SchemaVersioned;

public record EtaFeatureVector(
        String schemaVersion,
        double baselineMinutes,
        double trafficMultiplier,
        double weatherMultiplier,
        double distanceKm,
        int hourOfDay) implements SchemaVersioned {
}

