package com.routechain.backend.offer;

import java.time.Instant;

/**
 * Reservation lock for one order while driver offers compete.
 */
public record OfferReservation(
        String reservationId,
        String orderId,
        String offerBatchId,
        long reservationVersion,
        String driverId,
        Instant reservedAt,
        Instant expiresAt,
        String status
) {
    public OfferReservation {
        reservationId = reservationId == null || reservationId.isBlank() ? "reservation-unknown" : reservationId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        offerBatchId = offerBatchId == null || offerBatchId.isBlank() ? "offer-batch-unknown" : offerBatchId;
        driverId = driverId == null || driverId.isBlank() ? "driver-unknown" : driverId;
        reservedAt = reservedAt == null ? Instant.now() : reservedAt;
        expiresAt = expiresAt == null ? reservedAt : expiresAt;
        status = status == null || status.isBlank() ? "ACCEPTED" : status;
    }
}
