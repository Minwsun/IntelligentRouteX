package com.routechain.simulation;

import com.routechain.graph.GraphExplanationTrace;

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
        double graphAffinityScore,
        double edgeScore,
        boolean borrowed,
        String rationale,
        GraphExplanationTrace graphExplanationTrace
) {
    public MarketplaceEdge {
        edgeId = edgeId == null || edgeId.isBlank() ? "edge-unknown" : edgeId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        pickupCellId = pickupCellId == null || pickupCellId.isBlank() ? "cell-unknown" : pickupCellId;
        dropoffCellId = dropoffCellId == null || dropoffCellId.isBlank() ? "cell-unknown" : dropoffCellId;
        rationale = rationale == null ? "" : rationale;
        graphExplanationTrace = graphExplanationTrace == null
                ? new GraphExplanationTrace("graph-trace-unknown", driverId, orderId, pickupCellId, dropoffCellId,
                0.0, 0.0, 0.0, 0.0, 0.0, "")
                : graphExplanationTrace;
    }
}
