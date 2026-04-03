package com.routechain.data.service;

import com.google.gson.Gson;
import com.routechain.data.model.IdempotencyRecord;
import com.routechain.data.port.IdempotencyRepository;
import com.routechain.infra.GsonSupport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Typed helper around idempotency persistence.
 */
@Service
public class IdempotencyService {
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(2);
    private static final long POLL_INTERVAL_MS = 25L;

    private final IdempotencyRepository repository;
    private final Gson gson = GsonSupport.compact();

    public IdempotencyService(IdempotencyRepository repository) {
        this.repository = repository;
    }

    public <T> Optional<T> replay(String scope, String actorId, String idempotencyKey, Class<T> type) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return repository.find(scope, actorId, idempotencyKey)
                .filter(IdempotencyRecord::isCompleted)
                .map(record -> gson.fromJson(record.responseJson(), type));
    }

    public void remember(String scope, String actorId, String idempotencyKey, Object payload) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || payload == null) {
            return;
        }
        repository.save(new IdempotencyRecord(
                scope,
                actorId,
                idempotencyKey,
                gson.toJson(payload),
                Instant.now()
        ));
    }

    public <T> T executeOnce(String scope,
                             String actorId,
                             String idempotencyKey,
                             Class<T> type,
                             Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }
        Optional<T> replay = replay(scope, actorId, idempotencyKey, type);
        if (replay.isPresent()) {
            return replay.get();
        }

        Instant claimedAt = Instant.now();
        String claimToken = java.util.UUID.randomUUID().toString();
        Optional<IdempotencyRecord> existing = repository.claim(
                IdempotencyRecord.claimed(scope, actorId, idempotencyKey, claimToken, claimedAt)
        );
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.isCompleted()) {
                return gson.fromJson(record.responseJson(), type);
            }
            return waitForCompleted(scope, actorId, idempotencyKey, type, DEFAULT_WAIT);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException exception) {
            repository.complete(new IdempotencyRecord(
                    scope,
                    actorId,
                    idempotencyKey,
                    IdempotencyRecord.Status.FAILED,
                    claimToken,
                    "",
                    claimedAt,
                    Instant.now()
            ));
            throw exception;
        }

        repository.complete(IdempotencyRecord.completed(
                scope,
                actorId,
                idempotencyKey,
                claimToken,
                gson.toJson(result),
                claimedAt,
                Instant.now()
        ));
        return result;
    }

    private <T> T waitForCompleted(String scope,
                                   String actorId,
                                   String idempotencyKey,
                                   Class<T> type,
                                   Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> replay = replay(scope, actorId, idempotencyKey, type);
            if (replay.isPresent()) {
                return replay.get();
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Idempotent request is still in progress: " + scope + "/" + actorId);
    }
}
