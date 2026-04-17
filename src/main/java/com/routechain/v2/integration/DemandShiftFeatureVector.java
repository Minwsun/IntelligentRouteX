package com.routechain.v2.integration;

import com.routechain.v2.SchemaVersioned;

public record DemandShiftFeatureVector(
        String schemaVersion,
        String traceId,
        String corridorId,
        int orderCount,
        int urgentOrderCount,
        int driverCount,
        double averageReadySpreadMinutes,
        double averagePickupEtaMinutes,
        double averageCompletionEtaMinutes,
        double averageRouteValue,
        double averageBoundaryParticipation,
        int horizonMinutes) implements SchemaVersioned {
}
