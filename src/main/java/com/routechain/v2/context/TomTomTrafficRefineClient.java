package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TomTomTrafficRefineClient {
    private final RouteChainDispatchV2Properties.Tomtom properties;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TomTomTrafficRefineClient(RouteChainDispatchV2Properties.Tomtom properties) {
        this.properties = properties;
    }

    public double refineMultiplier(String cacheKey, double fallbackMultiplier) {
        if (properties == null || !properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return 1.0;
        }
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.multiplier();
        }
        double refined = clamp(fallbackMultiplier * 1.02, 1.0, 1.35);
        Duration ttl = properties.getCacheTtl() == null ? Duration.ofMinutes(30) : properties.getCacheTtl();
        cache.put(cacheKey, new CacheEntry(refined, Instant.now().plus(ttl)));
        return refined;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record CacheEntry(double multiplier, Instant expiresAt) {
    }
}
