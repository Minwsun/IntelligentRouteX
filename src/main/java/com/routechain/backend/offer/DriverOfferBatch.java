package com.routechain.backend.offer;

import java.time.Instant;
import java.util.List;

/**
 * One screened offer fanout batch for a single order.
 */
public record DriverOfferBatch(
        String offerBatchId,
        String orderId,
        String serviceTier,
        int fanout,
        Instant createdAt,
        Instant expiresAt,
        List<String> offerIds,
        List<DriverOfferCandidate> candidates
) {
    public DriverOfferBatch {
        offerBatchId = offerBatchId == null || offerBatchId.isBlank() ? "offer-batch-unknown" : offerBatchId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        fanout = Math.max(1, Math.min(3, fanout));
        createdAt = createdAt == null ? Instant.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt : expiresAt;
        offerIds = offerIds == null ? List.of() : List.copyOf(offerIds);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
