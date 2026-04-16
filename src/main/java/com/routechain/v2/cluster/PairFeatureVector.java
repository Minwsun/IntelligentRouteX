package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;

public record PairFeatureVector(
        String schemaVersion,
        String leftOrderId,
        String rightOrderId,
        double pickupDistanceKm,
        double pickupEtaMinutes,
        long readyGapMinutes,
        double dropAngleDiffDegrees,
        boolean sameCorridor,
        double mergeEtaRatio,
        double landingCompatibility,
        boolean weatherTightened) implements SchemaVersioned {
}

