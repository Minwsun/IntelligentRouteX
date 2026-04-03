package com.routechain.backend.offer;

import com.routechain.simulation.MarketplaceEdge;

import java.time.Instant;
import java.util.List;

/**
 * Planned screened offer batch generated from ranked marketplace edges.
 */
public record OfferBatchPlan(
        String orderId,
        String serviceTier,
        int fanout,
        Instant plannedAt,
        List<MarketplaceEdge> rankedEdges
) {
    public OfferBatchPlan {
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        plannedAt = plannedAt == null ? Instant.now() : plannedAt;
        rankedEdges = rankedEdges == null ? List.of() : List.copyOf(rankedEdges);
    }
}
