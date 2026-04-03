package com.routechain.data.model;

import java.time.Instant;

/**
 * One persisted lifecycle status transition for an order.
 */
public record OrderStatusHistoryRecord(
        String orderId,
        String status,
        String reason,
        Instant recordedAt
) {
    public OrderStatusHistoryRecord {
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        status = status == null || status.isBlank() ? "UNKNOWN" : status;
        reason = reason == null ? "" : reason;
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }
}
