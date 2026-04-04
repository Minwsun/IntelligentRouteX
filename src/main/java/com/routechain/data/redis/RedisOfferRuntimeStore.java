package com.routechain.data.redis;

import com.routechain.data.port.OfferRuntimeStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class RedisOfferRuntimeStore implements OfferRuntimeStore {
    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public RedisOfferRuntimeStore(StringRedisTemplate redisTemplate, String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = normalizeKeyPrefix(keyPrefix);
    }

    @Override
    public void markOfferActive(String offerId, Instant expiresAt) {
        if (offerId == null || offerId.isBlank() || expiresAt == null) {
            return;
        }
        Instant now = Instant.now();
        if (!expiresAt.isAfter(now)) {
            redisTemplate.delete(offerKey(offerId));
            return;
        }
        Duration ttl = Duration.between(now, expiresAt);
        redisTemplate.opsForValue().set(offerKey(offerId), expiresAt.toString(), ttl);
    }

    @Override
    public boolean isOfferActive(String offerId, Instant now) {
        if (offerId == null || offerId.isBlank()) {
            return false;
        }
        String raw = redisTemplate.opsForValue().get(offerKey(offerId));
        if (raw == null || raw.isBlank()) {
            return false;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        try {
            Instant expiresAt = Instant.parse(raw);
            if (!expiresAt.isAfter(effectiveNow)) {
                redisTemplate.delete(offerKey(offerId));
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            redisTemplate.delete(offerKey(offerId));
            return false;
        }
    }

    @Override
    public void clearOffer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return;
        }
        redisTemplate.delete(offerKey(offerId));
    }

    @Override
    public void markDriverCooldown(String driverId, Instant expiresAt) {
        if (driverId == null || driverId.isBlank() || expiresAt == null) {
            return;
        }
        Instant now = Instant.now();
        if (!expiresAt.isAfter(now)) {
            redisTemplate.delete(driverCooldownKey(driverId));
            return;
        }
        Duration ttl = Duration.between(now, expiresAt);
        redisTemplate.opsForValue().set(driverCooldownKey(driverId), expiresAt.toString(), ttl);
    }

    @Override
    public Optional<Instant> driverCooldownUntil(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return Optional.empty();
        }
        String raw = redisTemplate.opsForValue().get(driverCooldownKey(driverId));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            Instant expiresAt = Instant.parse(raw);
            if (!expiresAt.isAfter(Instant.now())) {
                redisTemplate.delete(driverCooldownKey(driverId));
                return Optional.empty();
            }
            return Optional.of(expiresAt);
        } catch (RuntimeException ex) {
            redisTemplate.delete(driverCooldownKey(driverId));
            return Optional.empty();
        }
    }

    private String offerKey(String offerId) {
        return keyPrefix + ":offer_runtime:" + offerId;
    }

    private String driverCooldownKey(String driverId) {
        return keyPrefix + ":driver_cooldown:" + driverId;
    }

    private String normalizeKeyPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "routechain";
        }
        String normalized = value;
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "routechain" : normalized;
    }
}
