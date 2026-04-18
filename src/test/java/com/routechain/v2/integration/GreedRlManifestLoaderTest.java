package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreedRlManifestLoaderTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:greedrl-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void loadsGreedRlManifestEntry() throws Exception {
        Path manifestPath = HttpGreedRlTestSupport.manifest(tempDir, "v1", "sha256:greedrl", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("model-manifest/v1", manifest.schemaVersion());
        assertEquals("v1", manifest.worker("ml-greedrl-worker").modelVersion());
    }

    @Test
    void loadsGreedRlManifestV2LocalModelFields() throws Exception {
        Path manifestPath = HttpGreedRlTestSupport.manifestV2(
                tempDir,
                "v1",
                "sha256:greedrl",
                "dispatch-v2-ml/v1",
                "dispatch-v2-java/v1",
                LOADED_MODEL_FINGERPRINT);

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("model-manifest/v2", manifest.schemaVersion());
        assertEquals("materialized/greedrl", manifest.worker("ml-greedrl-worker").localModelRoot());
        assertEquals("LOCAL_PACKAGE_PROMOTION", manifest.worker("ml-greedrl-worker").materializationMode());
        assertEquals(LOADED_MODEL_FINGERPRINT, manifest.worker("ml-greedrl-worker").loadedModelFingerprint());
        assertEquals("==3.8.*", manifest.worker("ml-greedrl-worker").sourcePythonRequirement());
    }
}
