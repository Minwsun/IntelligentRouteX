package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForecastManifestLoaderTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:chronos-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void loadsForecastWorkerManifestEntry() throws Exception {
        Path manifestPath = HttpForecastTestSupport.manifest(tempDir, "v1", "sha256:chronos", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("v1", manifest.worker("ml-forecast-worker").modelVersion());
    }

    @Test
    void loadsForecastWorkerManifestV2LocalModelFields() throws Exception {
        Path manifestPath = HttpForecastTestSupport.manifestV2(
                tempDir,
                "v1",
                "sha256:chronos",
                "dispatch-v2-ml/v1",
                "dispatch-v2-java/v1",
                LOADED_MODEL_FINGERPRINT);

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        WorkerManifest.WorkerManifestEntry worker = manifest.worker("ml-forecast-worker");
        assertEquals("model-manifest/v2", manifest.schemaVersion());
        assertEquals("materialized/chronos-2", worker.localModelRoot());
        assertEquals("materialized/chronos-2/model/chronos-runtime-manifest.json", worker.localArtifactPath());
        assertEquals("HF_SNAPSHOT_PROMOTION", worker.materializationMode());
        assertEquals(LOADED_MODEL_FINGERPRINT, worker.loadedModelFingerprint());
        assertEquals("https://github.com/amazon-science/chronos-forecasting.git", worker.sourceRepository());
        assertEquals("amazon/chronos-2", worker.sourceModelId());
        assertEquals("0f8a440441931157957e2be1a9bce66627d99c76", worker.sourceModelRevision());
        assertEquals("chronos-forecasting==2.2.2", worker.sourcePackageRequirement());
    }
}
