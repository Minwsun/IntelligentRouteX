package com.routechain.ai;

import java.util.List;

/**
 * Lightweight coverage snapshot for one driver-facing dispatch cluster.
 */
public record DriverAccessCluster(
        String clusterId,
        String zoneId,
        List<String> driverIds,
        List<String> borrowedDriverIds,
        double radiusKm,
        double approachEtaP50Minutes,
        double replacementDepth,
        double coverageQuality,
        double continuationHealth,
        double endZoneDemandSupport,
        double borrowedDependencyScore,
        boolean borrowed
) {
    public DriverAccessCluster {
        clusterId = clusterId == null || clusterId.isBlank() ? "cluster-unknown" : clusterId;
        zoneId = zoneId == null || zoneId.isBlank() ? "zone-unknown" : zoneId;
        driverIds = driverIds == null ? List.of() : List.copyOf(driverIds);
        borrowedDriverIds = borrowedDriverIds == null ? List.of() : List.copyOf(borrowedDriverIds);
    }
}
