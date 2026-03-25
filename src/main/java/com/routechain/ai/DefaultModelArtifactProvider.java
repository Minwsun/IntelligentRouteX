package com.routechain.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Local-first artifact provider. Current behavior is fallback-first while
 * preserving a stable interface for future MLflow / artifact registry loading.
 */
public final class DefaultModelArtifactProvider implements ModelArtifactProvider {
    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String artifactKey, Supplier<T> fallbackFactory) {
        return (T) artifacts.computeIfAbsent(artifactKey, key -> fallbackFactory.get());
    }

    @Override
    public String activeModelVersion(String artifactKey) {
        return artifactKey + "-online-fallback-v1";
    }

    @Override
    public String featureSchemaId(String artifactKey) {
        return artifactKey + "-schema-v1";
    }
}
