package com.routechain.ai;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Local-first artifact provider. Current behavior is fallback-first while
 * preserving a stable interface for future MLflow / artifact registry loading.
 */
public final class DefaultModelArtifactProvider implements ModelArtifactProvider {
    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();
    private final Map<String, ModelBundleManifest> activeBundles = new ConcurrentHashMap<>();
    private final Map<String, List<ModelBundleManifest>> challengerBundles = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String artifactKey, Supplier<T> fallbackFactory) {
        return (T) artifacts.computeIfAbsent(artifactKey, key -> fallbackFactory.get());
    }

    @Override
    public String activeModelVersion(String artifactKey) {
        return activeBundle(artifactKey).modelVersion();
    }

    @Override
    public String featureSchemaId(String artifactKey) {
        return activeBundle(artifactKey).featureSchema();
    }

    @Override
    public ModelBundleManifest activeBundle(String artifactKey) {
        String key = normalizeKey(artifactKey);
        return activeBundles.computeIfAbsent(key, this::defaultChampionBundle);
    }

    @Override
    public List<ModelBundleManifest> challengerBundles(String artifactKey) {
        return challengerBundles.getOrDefault(normalizeKey(artifactKey), List.of());
    }

    @Override
    public void registerBundle(ModelBundleManifest manifest) {
        if (manifest == null) {
            return;
        }
        String key = normalizeKey(manifest.modelKey());
        if (manifest.champion()) {
            activeBundles.put(key, manifest);
            return;
        }
        List<ModelBundleManifest> existing = challengerBundles.getOrDefault(key, List.of());
        List<ModelBundleManifest> merged = new java.util.ArrayList<>(existing);
        merged.removeIf(candidate -> candidate.modelVersion().equals(manifest.modelVersion()));
        merged.add(manifest);
        merged.sort(java.util.Comparator.comparing(ModelBundleManifest::modelVersion));
        challengerBundles.put(key, List.copyOf(merged));
    }

    @Override
    public boolean promoteBundle(String artifactKey, String modelVersion) {
        String key = normalizeKey(artifactKey);
        if (modelVersion == null || modelVersion.isBlank()) {
            return false;
        }
        if (activeBundle(key).modelVersion().equals(modelVersion)) {
            return true;
        }
        List<ModelBundleManifest> challengers = challengerBundles.getOrDefault(key, List.of());
        for (ModelBundleManifest challenger : challengers) {
            if (challenger.modelVersion().equals(modelVersion)) {
                activeBundles.put(key, new ModelBundleManifest(
                        challenger.modelKey(),
                        challenger.modelVersion(),
                        challenger.featureSchema(),
                        challenger.onnxPath(),
                        challenger.latencyBudgetMs(),
                        challenger.fallbackPolicy(),
                        true
                ));
                List<ModelBundleManifest> remaining = challengers.stream()
                        .filter(candidate -> !candidate.modelVersion().equals(modelVersion))
                        .toList();
                challengerBundles.put(key, remaining);
                return true;
            }
        }
        return false;
    }

    private String normalizeKey(String artifactKey) {
        return artifactKey == null || artifactKey.isBlank() ? "unknown-model" : artifactKey;
    }

    private ModelBundleManifest defaultChampionBundle(String artifactKey) {
        return new ModelBundleManifest(
                artifactKey,
                artifactKey + "-online-fallback-v1",
                artifactKey + "-schema-v1",
                "offline://fallback/" + artifactKey,
                120,
                "heuristic-fallback",
                true
        );
    }
}
