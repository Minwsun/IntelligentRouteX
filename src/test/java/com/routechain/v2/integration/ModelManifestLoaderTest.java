package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelManifestLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsTabularWorkerManifestEntry() throws Exception {
        Path manifestPath = HttpTabularTestSupport.manifest(tempDir, "v1", "sha256:test", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");

        TabularWorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("model-manifest/v1", manifest.schemaVersion());
        assertEquals("v1", manifest.worker("ml-tabular-worker").modelVersion());
    }
}
