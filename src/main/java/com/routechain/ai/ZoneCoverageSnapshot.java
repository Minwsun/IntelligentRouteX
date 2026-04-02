package com.routechain.ai;

/**
 * Zone-level coverage snapshot for empty-mile aware dispatch and reserve shaping.
 */
public record ZoneCoverageSnapshot(
        String zoneId,
        int currentSupply,
        int targetCoverage,
        double postDropDemandProbability,
        double emptyZoneRisk,
        int borrowIn,
        int borrowOut,
        int reserveTarget,
        double borrowPressure
) {
    public ZoneCoverageSnapshot {
        zoneId = zoneId == null || zoneId.isBlank() ? "zone-unknown" : zoneId;
    }
}
