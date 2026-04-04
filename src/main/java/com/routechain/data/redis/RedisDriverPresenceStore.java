package com.routechain.data.redis;

import com.routechain.data.port.DriverPresenceStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public class RedisDriverPresenceStore implements DriverPresenceStore {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final Duration defaultTtl;

    public RedisDriverPresenceStore(StringRedisTemplate redisTemplate,
                                    String keyPrefix,
                                    Duration defaultTtl) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = normalizeKeyPrefix(keyPrefix);
        this.defaultTtl = normalizeTtl(defaultTtl);
    }

    @Override
    public void heartbeat(String driverId, double lat, double lng, boolean available, Duration ttl) {
        Duration effectiveTtl = normalizeTtl(ttl);
        Instant expiresAt = Instant.now().plus(effectiveTtl);
        String key = presenceKey(driverId);
        redisTemplate.opsForHash().putAll(key, Map.of(
                "driverId", driverId,
                "lat", Double.toString(lat),
                "lng", Double.toString(lng),
                "available", Boolean.toString(available),
                "expiresAt", expiresAt.toString()
        ));
        redisTemplate.expire(key, effectiveTtl);
    }

    @Override
    public Optional<PresenceSnapshot> find(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return Optional.empty();
        }
        String key = presenceKey(driverId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        try {
            Instant expiresAt = Instant.parse(value(entries, "expiresAt"));
            if (!expiresAt.isAfter(Instant.now())) {
                redisTemplate.delete(key);
                return Optional.empty();
            }
            return Optional.of(new PresenceSnapshot(
                    value(entries, "driverId"),
                    Double.parseDouble(value(entries, "lat")),
                    Double.parseDouble(value(entries, "lng")),
                    Boolean.parseBoolean(value(entries, "available")),
                    expiresAt
            ));
        } catch (RuntimeException ex) {
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private String presenceKey(String driverId) {
        return keyPrefix + ":driver_presence:" + driverId;
    }

    private Duration normalizeTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return defaultTtl == null || defaultTtl.isZero() || defaultTtl.isNegative()
                    ? DEFAULT_TTL
                    : defaultTtl;
        }
        return ttl;
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

    private String value(Map<Object, Object> entries, String key) {
        Object raw = entries.get(key);
        if (raw == null) {
            throw new IllegalStateException("Missing Redis field: " + key);
        }
        return raw.toString();
    }
}
