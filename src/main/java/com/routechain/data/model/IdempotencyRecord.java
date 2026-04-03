package com.routechain.data.model;

import java.time.Instant;

/**
 * Stored idempotency response for write APIs.
 */
public record IdempotencyRecord(
        String scope,
        String actorId,
        String idempotencyKey,
        Status status,
        String claimToken,
        String responseJson,
        Instant createdAt,
        Instant completedAt
) {
    public IdempotencyRecord {
        scope = scope == null || scope.isBlank() ? "unknown" : scope;
        actorId = actorId == null ? "" : actorId;
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? "idempotency-unknown" : idempotencyKey;
        status = status == null ? Status.COMPLETED : status;
        claimToken = claimToken == null ? "" : claimToken;
        responseJson = responseJson == null ? "" : responseJson;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        completedAt = completedAt == null && status == Status.COMPLETED ? createdAt : completedAt;
    }

    public IdempotencyRecord(String scope,
                             String actorId,
                             String idempotencyKey,
                             String responseJson,
                             Instant createdAt) {
        this(scope, actorId, idempotencyKey, Status.COMPLETED, "", responseJson, createdAt, createdAt);
    }

    public static IdempotencyRecord claimed(String scope,
                                            String actorId,
                                            String idempotencyKey,
                                            String claimToken,
                                            Instant claimedAt) {
        return new IdempotencyRecord(scope, actorId, idempotencyKey, Status.IN_PROGRESS, claimToken, "", claimedAt, null);
    }

    public static IdempotencyRecord completed(String scope,
                                              String actorId,
                                              String idempotencyKey,
                                              String claimToken,
                                              String responseJson,
                                              Instant claimedAt,
                                              Instant completedAt) {
        return new IdempotencyRecord(scope, actorId, idempotencyKey, Status.COMPLETED, claimToken, responseJson, claimedAt, completedAt);
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isInProgress() {
        return status == Status.IN_PROGRESS;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public enum Status {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
