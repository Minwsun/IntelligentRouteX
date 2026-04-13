package com.routechain.data.model;

import java.time.Instant;

public record OrderLifecycleFact(
        String factId,
        String orderId,
        OrderLifecycleFactType factType,
        Instant recordedAt,
        String actorType,
        String actorId,
        String idempotencyKey,
        String correlationId,
        String payloadJson
) {
    public OrderLifecycleFact {
        factId = factId == null || factId.isBlank() ? "fact-unknown" : factId;
        orderId = orderId == null || orderId.isBlank() ? "order-unknown" : orderId;
        factType = factType == null ? OrderLifecycleFactType.ORDER_CREATED : factType;
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
        actorType = actorType == null || actorType.isBlank() ? "SYSTEM" : actorType;
        actorId = actorId == null ? "" : actorId;
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey;
        correlationId = correlationId == null ? "" : correlationId;
        payloadJson = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson;
    }
}
