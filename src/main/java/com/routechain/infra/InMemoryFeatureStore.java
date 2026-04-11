package com.routechain.infra;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local-first in-memory feature store.
 */
public final class InMemoryFeatureStore implements FeatureStore {
    private final Map<String, Map<String, Map<String, Object>>> namespaces = new ConcurrentHashMap<>();

    @Override
    public void put(String namespace, String key, Map<String, Object> value) {
        namespaces.computeIfAbsent(namespace, ignored -> new ConcurrentHashMap<>())
                .put(key, Map.copyOf(value));
    }

    @Override
    public Optional<Map<String, Object>> get(String namespace, String key) {
        return Optional.ofNullable(namespaces.getOrDefault(namespace, Map.of()).get(key));
    }

    @Override
    public Map<String, Map<String, Object>> scan(String namespace, String keyPrefix) {
        Map<String, Map<String, Object>> entries = namespaces.getOrDefault(namespace, Map.of());
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return Map.copyOf(entries);
        }
        Map<String, Map<String, Object>> filtered = new ConcurrentHashMap<>();
        entries.forEach((key, value) -> {
            if (key.startsWith(keyPrefix)) {
                filtered.put(key, value);
            }
        });
        return Map.copyOf(filtered);
    }
}
