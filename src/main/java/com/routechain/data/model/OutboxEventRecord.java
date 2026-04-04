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
        int attemptCount,
        Instant createdAt,
        Instant publishedAt
        ,
        Instant nextAttemptAt,
        String lastError,
        String claimedBy,
        Instant claimedAt,
        String correlationId
) {
    public OutboxEventRecord {
        eventId = eventId == null || eventId.isBlank() ? "outbox-unknown" : eventId;
        topicKey = topicKey == null || topicKey.isBlank() ? "routechain.event.v1" : topicKey;
        aggregateType = aggregateType == null || aggregateType.isBlank() ? "UNKNOWN" : aggregateType;
        aggregateId = aggregateId == null ? "" : aggregateId;
        eventType = eventType == null || eventType.isBlank() ? "UnknownEvent" : eventType;
        payloadJson = payloadJson == null ? "{}" : payloadJson;
        status = status == null || status.isBlank() ? "PENDING" : status;
        attemptCount = Math.max(0, attemptCount);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        nextAttemptAt = nextAttemptAt == null ? createdAt : nextAttemptAt;
        lastError = lastError == null ? "" : lastError;
        claimedBy = claimedBy == null ? "" : claimedBy;
        correlationId = correlationId == null ? "" : correlationId;
    }

    public static OutboxEventRecord pending(String eventId,
                                            String topicKey,
                                            String aggregateType,
                                            String aggregateId,
                                            String eventType,
                                            String payloadJson,
                                            Instant createdAt,
                                            String correlationId) {
        return new OutboxEventRecord(
                eventId,
                topicKey,
                aggregateType,
                aggregateId,
                eventType,
                payloadJson,
                "PENDING",
                0,
                createdAt,
                null,
                createdAt,
                "",
                "",
                null,
                correlationId
        );
    }
}
