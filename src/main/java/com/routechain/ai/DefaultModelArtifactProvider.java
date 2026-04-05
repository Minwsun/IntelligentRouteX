package com.routechain.ai;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Local-first artifact provider. Current behavior is fallback-first while
 * preserving a stable interface for future MLflow / artifact registry loading.
 */
public final class DefaultModelArtifactProvider implements ModelArtifactProvider {
    private static final Gson GSON = GsonSupport.compact();
    private static final Path DEFAULT_REGISTRY_PATH = Path.of("models", "model-registry-v1.json");

    private final Map<String, Object> artifacts = new ConcurrentHashMap<>();
    private final Map<String, ModelBundleManifest> activeBundles = new ConcurrentHashMap<>();
    private final Map<String, List<ModelBundleManifest>> challengerBundles = new ConcurrentHashMap<>();

    public DefaultModelArtifactProvider() {
        this(DEFAULT_REGISTRY_PATH);
    }

    DefaultModelArtifactProvider(Path registryPath) {
        loadRegistrySnapshot(registryPath);
    }

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

    private void loadRegistrySnapshot(Path registryPath) {
        if (registryPath == null || Files.notExists(registryPath)) {
            return;
        }
        try {
            RegistrySnapshot snapshot = GSON.fromJson(
                    Files.readString(registryPath, StandardCharsets.UTF_8),
                    RegistrySnapshot.class);
            if (snapshot == null || snapshot.models == null) {
                return;
            }
            for (RegistryEntry entry : snapshot.models) {
                if (entry == null || entry.modelKey == null || entry.modelKey.isBlank()) {
                    continue;
                }
                registerBundle(toManifest(entry));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load model registry snapshot from " + registryPath, e);
        }
    }

    private ModelBundleManifest toManifest(RegistryEntry entry) {
        String normalizedKey = normalizeKey(entry.canonicalModelKey());
        return new ModelBundleManifest(
                normalizedKey,
                entry.modelVersion,
                entry.featureSchema == null || entry.featureSchema.isBlank()
                        ? normalizedKey + "-schema-v1"
                        : entry.featureSchema,
                entry.sourceUri,
                entry.latencyBudgetMs == null ? 120 : entry.latencyBudgetMs,
                entry.fallbackPolicy,
                entry.isChampion()
        );
    }

    private static final class RegistrySnapshot {
        List<RegistryEntry> models = new ArrayList<>();
    }

    private static final class RegistryEntry {
        String modelKey;
        String modelVersion;
        String sourceUri;
        String featureSchema;
        Integer latencyBudgetMs;
        String fallbackPolicy;
        @SerializedName("champion")
        Boolean champion;

        private String canonicalModelKey() {
            if (modelKey != null && modelKey.endsWith("-challenger")) {
                return modelKey.substring(0, modelKey.length() - "-challenger".length()) + "-model";
            }
            return modelKey;
        }

        private boolean isChampion() {
            if (champion != null) {
                return champion;
            }
            return modelKey == null || !modelKey.endsWith("-challenger");
        }
    }
}
