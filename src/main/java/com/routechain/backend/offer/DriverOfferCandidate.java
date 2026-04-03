package com.routechain.backend.offer;

/**
 * Screened driver candidate for an offer batch.
 */
public record DriverOfferCandidate(
        String orderId,
        String driverId,
        String serviceTier,
        double score,
        double acceptanceProbability,
        double deadheadKm,
        boolean borrowed,
        String rationale
) {
    public DriverOfferCandidate {
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        rationale = rationale == null ? "" : rationale;
        acceptanceProbability = Math.max(0.0, Math.min(1.0, acceptanceProbability));
        deadheadKm = Math.max(0.0, deadheadKm);
    }
}
