package com.routechain.simulation;

/**
 * Read-only marketplace edge used by control-room network views.
 */
public record MarketplaceEdge(
        String edgeId,
        String driverId,
        String orderId,
        String serviceTier,
        String pickupCellId,
        String dropoffCellId,
        double pickupEtaMinutes,
        double deadheadKm,
        double executionScore,
        double continuationScore,
        double edgeScore,
        boolean borrowed,
        String rationale
) {
    public MarketplaceEdge {
        edgeId = edgeId == null || edgeId.isBlank() ? "edge-unknown" : edgeId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        pickupCellId = pickupCellId == null || pickupCellId.isBlank() ? "cell-unknown" : pickupCellId;
        dropoffCellId = dropoffCellId == null || dropoffCellId.isBlank() ? "cell-unknown" : dropoffCellId;
        rationale = rationale == null ? "" : rationale;
    }
}
