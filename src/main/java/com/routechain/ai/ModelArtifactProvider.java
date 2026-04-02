package com.routechain.ai;

import java.util.List;
import java.util.function.Supplier;

/**
 * Resolves champion/challenger model artifacts with a deterministic fallback.
 */
public interface ModelArtifactProvider {
    <T> T getOrDefault(String artifactKey, Supplier<T> fallbackFactory);
    String activeModelVersion(String artifactKey);
    String featureSchemaId(String artifactKey);

    default ModelBundleManifest activeBundle(String artifactKey) {
        return new ModelBundleManifest(
                artifactKey,
                activeModelVersion(artifactKey),
                featureSchemaId(artifactKey),
                "offline://fallback/" + artifactKey,
                120,
                "heuristic-fallback",
                true
        );
    }

    default List<ModelBundleManifest> challengerBundles(String artifactKey) {
        return List.of();
    }

    default void registerBundle(ModelBundleManifest manifest) {
        // optional extension point
    }

    default boolean promoteBundle(String artifactKey, String modelVersion) {
        return false;
    }
}
