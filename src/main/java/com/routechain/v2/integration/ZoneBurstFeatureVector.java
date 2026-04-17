package com.routechain.v2.integration;

import com.routechain.v2.SchemaVersioned;

public record ZoneBurstFeatureVector(
        String schemaVersion,
        String traceId,
        String corridorId,
        int orderCount,
        int urgentOrderCount,
        int driverCount,
        double averageCompletionEtaMinutes,
        double averageRouteValue,
        double averageBundleScore,
        double averagePairSupport,
        int horizonMinutes) implements SchemaVersioned {
}
