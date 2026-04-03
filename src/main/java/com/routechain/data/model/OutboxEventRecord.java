package com.routechain.data.model;

import java.time.Instant;

/**
 * Durable outbox event to be published to Kafka or other downstream systems.
 */
public record OutboxEventRecord(
        String eventId,
        String topicKey,
        String aggregateType,
        String aggregateId,
        String eventType,
        String payloadJson,
        String status,
        Instant createdAt,
        Instant publishedAt
) {
    public OutboxEventRecord {
        eventId = eventId == null || eventId.isBlank() ? "outbox-unknown" : eventId;
        topicKey = topicKey == null || topicKey.isBlank() ? "routechain.event.v1" : topicKey;
        aggregateType = aggregateType == null || aggregateType.isBlank() ? "UNKNOWN" : aggregateType;
        aggregateId = aggregateId == null ? "" : aggregateId;
        eventType = eventType == null || eventType.isBlank() ? "UnknownEvent" : eventType;
        payloadJson = payloadJson == null ? "{}" : payloadJson;
        status = status == null || status.isBlank() ? "PENDING" : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
