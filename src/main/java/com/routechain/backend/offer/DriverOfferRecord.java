package com.routechain.backend.offer;

import java.time.Instant;

/**
 * Persisted offer row used by the broker state store.
 */
public record DriverOfferRecord(
        String offerId,
        String offerBatchId,
        String orderId,
        String driverId,
        String serviceTier,
        double score,
        double acceptanceProbability,
        double deadheadKm,
        boolean borrowed,
        String rationale,
        DriverOfferStatus status,
        Instant createdAt,
        Instant expiresAt
) {
    public DriverOfferRecord {
        offerId = offerId == null || offerId.isBlank() ? "offer-unknown" : offerId;
        offerBatchId = offerBatchId == null || offerBatchId.isBlank() ? "offer-batch-unknown" : offerBatchId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        rationale = rationale == null ? "" : rationale;
        status = status == null ? DriverOfferStatus.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt : expiresAt;
    }

    public DriverOfferRecord withStatus(DriverOfferStatus nextStatus) {
        return new DriverOfferRecord(
                offerId,
                offerBatchId,
                orderId,
                driverId,
                serviceTier,
                score,
                acceptanceProbability,
                deadheadKm,
                borrowed,
                rationale,
                nextStatus,
                createdAt,
                expiresAt
        );
    }

    public OfferBrokerService.OfferView toView() {
        return new OfferBrokerService.OfferView(
                offerId,
                offerBatchId,
                orderId,
                driverId,
                serviceTier,
                score,
                acceptanceProbability,
                deadheadKm,
                borrowed,
                rationale,
                status,
                createdAt,
                expiresAt
        );
    }
}
