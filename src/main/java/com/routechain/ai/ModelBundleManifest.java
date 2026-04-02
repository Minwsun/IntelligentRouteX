package com.routechain.ai;

/**
 * Registry contract for a model bundle served in the Java runtime.
 */
public record ModelBundleManifest(
        String modelKey,
        String modelVersion,
        String featureSchema,
        String onnxPath,
        int latencyBudgetMs,
        String fallbackPolicy,
        boolean champion
) {
    public ModelBundleManifest {
        modelKey = modelKey == null || modelKey.isBlank() ? "unknown-model" : modelKey;
        modelVersion = modelVersion == null || modelVersion.isBlank() ? modelKey + "-fallback-v1" : modelVersion;
        featureSchema = featureSchema == null || featureSchema.isBlank() ? modelKey + "-schema-v1" : featureSchema;
        onnxPath = onnxPath == null || onnxPath.isBlank() ? "offline://fallback/" + modelKey : onnxPath;
        latencyBudgetMs = Math.max(10, latencyBudgetMs);
        fallbackPolicy = fallbackPolicy == null || fallbackPolicy.isBlank() ? "heuristic-fallback" : fallbackPolicy;
    }
}
