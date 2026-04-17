package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

public record RouteValueFeatureVector(
        String schemaVersion,
        String traceId,
        String proposalId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        String routeSource,
        double projectedPickupEtaMinutes,
        double projectedCompletionEtaMinutes,
        double deterministicRouteValue,
        double rerankScore,
        double bundleScore,
        double anchorScore,
        double averagePairSupport,
        double urgencyLift,
        double boundaryPenalty,
        double fallbackPenalty) implements SchemaVersioned {
}
