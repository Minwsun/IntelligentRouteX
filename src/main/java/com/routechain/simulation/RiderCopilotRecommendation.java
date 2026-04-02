package com.routechain.simulation;

/**
 * Read-only guidance stream for future driver-facing product lanes.
 */
public record RiderCopilotRecommendation(
        String recommendationId,
        String driverSegment,
        String serviceTier,
        String action,
        String targetCellId,
        double targetLat,
        double targetLng,
        double priorityScore,
        double continuationOpportunity,
        double emptyZoneRisk,
        double reserveSupport,
        String reason
) {
    public RiderCopilotRecommendation {
        recommendationId = recommendationId == null || recommendationId.isBlank()
                ? "rec-unknown"
                : recommendationId;
        driverSegment = driverSegment == null || driverSegment.isBlank()
                ? "idle-driver"
                : driverSegment;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        action = action == null || action.isBlank() ? "STAY_READY" : action;
        targetCellId = targetCellId == null || targetCellId.isBlank()
                ? "GRID-unknown"
                : targetCellId;
        reason = reason == null ? "" : reason;
    }
}
