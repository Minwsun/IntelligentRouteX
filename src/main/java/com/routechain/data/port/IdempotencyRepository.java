package com.routechain.data.port;

import com.routechain.data.model.IdempotencyRecord;

import java.util.Optional;

public interface IdempotencyRepository {
    Optional<IdempotencyRecord> find(String scope, String actorId, String idempotencyKey);
    void save(IdempotencyRecord record);

    default Optional<IdempotencyRecord> claim(IdempotencyRecord record) {
        throw new UnsupportedOperationException("claim not implemented");
    }

    default void complete(IdempotencyRecord record) {
        save(record);
    }
}
