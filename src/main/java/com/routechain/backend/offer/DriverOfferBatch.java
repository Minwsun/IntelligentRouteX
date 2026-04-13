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
        int wave,
        String previousBatchId,
        Instant closedAt,
        String closeReason,
        List<String> offerIds,
        List<DriverOfferCandidate> candidates
) {
    public DriverOfferBatch(
            String offerBatchId,
            String orderId,
            String serviceTier,
            int fanout,
            Instant createdAt,
            Instant expiresAt,
            List<String> offerIds,
            List<DriverOfferCandidate> candidates
    ) {
        this(offerBatchId, orderId, serviceTier, fanout, createdAt, expiresAt, 1, "", null, "", offerIds, candidates);
    }

    public DriverOfferBatch {
        offerBatchId = offerBatchId == null || offerBatchId.isBlank() ? "offer-batch-unknown" : offerBatchId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        fanout = Math.max(0, Math.min(3, fanout));
        createdAt = createdAt == null ? Instant.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt : expiresAt;
        wave = Math.max(1, wave);
        previousBatchId = previousBatchId == null ? "" : previousBatchId;
        closeReason = closeReason == null ? "" : closeReason;
        offerIds = offerIds == null ? List.of() : List.copyOf(offerIds);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    public DriverOfferBatch close(Instant when, String reason) {
        return new DriverOfferBatch(
                offerBatchId,
                orderId,
                serviceTier,
                fanout,
                createdAt,
                expiresAt,
                wave,
                previousBatchId,
                when == null ? Instant.now() : when,
                reason == null ? "" : reason,
                offerIds,
                candidates
        );
    }
}
