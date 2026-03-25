package com.routechain.infra;

import java.util.Map;
import java.util.Optional;

/**
 * Online feature store abstraction for dispatch decisions.
 */
public interface FeatureStore {
    void put(String namespace, String key, Map<String, Object> value);
    Optional<Map<String, Object>> get(String namespace, String key);
}
