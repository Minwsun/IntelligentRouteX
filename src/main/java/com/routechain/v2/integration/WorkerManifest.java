package com.routechain.v2.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record WorkerManifest(
        String schemaVersion,
        List<WorkerManifestEntry> workers) {

    public WorkerManifestEntry worker(String workerName) {
        return workers.stream()
                .filter(entry -> entry.workerName().equals(workerName))
                .findFirst()
                .orElse(null);
    }

    public record WorkerManifestEntry(
            @JsonProperty("worker_name")
            String workerName,
            @JsonProperty("model_name")
            String modelName,
            @JsonProperty("model_version")
            String modelVersion,
            @JsonProperty("artifact_digest")
            String artifactDigest,
            @JsonProperty("rollback_artifact_digest")
            String rollbackArtifactDigest,
            @JsonProperty("runtime_image")
            String runtimeImage,
            @JsonProperty("compatibility_contract_version")
            String compatibilityContractVersion,
            @JsonProperty("min_supported_java_contract_version")
            String minSupportedJavaContractVersion,
            @JsonProperty("local_model_root")
            String localModelRoot,
            @JsonProperty("local_artifact_path")
            String localArtifactPath,
            @JsonProperty("materialization_mode")
            String materializationMode,
            @JsonProperty("ready_requires_local_load")
            Boolean readyRequiresLocalLoad,
            @JsonProperty("offline_boot_supported")
            Boolean offlineBootSupported,
            @JsonProperty("loaded_model_fingerprint")
            String loadedModelFingerprint,
            @JsonProperty("startup_warmup_request")
            StartupWarmupRequest startupWarmupRequest) {
    }

    public record StartupWarmupRequest(
            String endpoint,
            Map<String, Object> payload) {
    }
}
