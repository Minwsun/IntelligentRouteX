package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;

public record DriverFitFeatureVector(
        String schemaVersion,
        String traceId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        double pickupEtaMinutes,
        double etaUncertainty,
        double bundleScore,
        double anchorScore,
        double bundleSupportScore,
        double corridorAffinity,
        double urgencyLift,
        double boundaryPenalty,
        double driverFitScore) implements SchemaVersioned {
}
