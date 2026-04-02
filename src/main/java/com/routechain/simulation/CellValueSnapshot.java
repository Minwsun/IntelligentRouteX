package com.routechain.simulation;

/**
 * Snapshot of one spatial cell used by the city twin / control-room demo.
 */
public record CellValueSnapshot(
        String cellId,
        String spatialIndex,
        String serviceTier,
        double centerLat,
        double centerLng,
        double currentDemand,
        double demandForecast5m,
        double demandForecast10m,
        double demandForecast15m,
        double demandForecast30m,
        double shortageForecast10m,
        double trafficForecast10m,
        double weatherForecast10m,
        double postDropOpportunity10m,
        double emptyZoneRisk10m,
        double reserveTargetScore,
        double borrowPressure,
        double compositeValue
) {
    public CellValueSnapshot {
        cellId = cellId == null || cellId.isBlank() ? "cell-unknown" : cellId;
        spatialIndex = spatialIndex == null || spatialIndex.isBlank()
                ? "grid-fallback"
                : spatialIndex;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
    }
}
