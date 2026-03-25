package com.routechain.ai;

import java.util.function.Supplier;

/**
 * Resolves champion/challenger model artifacts with a deterministic fallback.
 */
public interface ModelArtifactProvider {
    <T> T getOrDefault(String artifactKey, Supplier<T> fallbackFactory);
    String activeModelVersion(String artifactKey);
    String featureSchemaId(String artifactKey);
}
