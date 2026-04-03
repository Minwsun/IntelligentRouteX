package com.routechain.backend.offer;

import java.time.Instant;

/**
 * Decision outcome for a single driver offer.
 */
public record OfferDecision(
        String offerId,
        String orderId,
        String driverId,
        DriverOfferStatus status,
        String reason,
        Instant decidedAt,
        long reservationVersion
) {
    public OfferDecision {
        offerId = offerId == null || offerId.isBlank() ? "offer-unknown" : offerId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        status = status == null ? DriverOfferStatus.LOST : status;
        reason = reason == null ? "" : reason;
        decidedAt = decidedAt == null ? Instant.now() : decidedAt;
    }
}
