package com.routechain.simulation;

/**
 * Driver-specific future value recommendation derived from the city twin.
 */
public record DriverFutureValue(
        String driverId,
        String currentCellId,
        String targetCellId,
        String serviceTier,
        int horizonMinutes,
        double currentZoneValue,
        double targetZoneValue,
        double postDropOpportunity,
        double emptyZoneRisk,
        double reserveSupport,
        double futureValueScore,
        String recommendation
) {
    public DriverFutureValue {
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        currentCellId = currentCellId == null || currentCellId.isBlank() ? "cell-unknown" : currentCellId;
        targetCellId = targetCellId == null || targetCellId.isBlank() ? "cell-unknown" : targetCellId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        horizonMinutes = Math.max(5, horizonMinutes);
        recommendation = recommendation == null ? "" : recommendation;
    }
}
