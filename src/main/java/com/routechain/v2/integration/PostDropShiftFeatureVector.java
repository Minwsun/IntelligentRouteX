package com.routechain.v2.integration;

import com.routechain.v2.SchemaVersioned;

public record PostDropShiftFeatureVector(
        String schemaVersion,
        String traceId,
        String corridorId,
        int orderCount,
        int urgentOrderCount,
        int driverCount,
        double averageCompletionEtaMinutes,
        double averageRouteValue,
        double averageDriverRerankScore,
        double averageStabilityProxy,
        int horizonMinutes) implements SchemaVersioned {
}
