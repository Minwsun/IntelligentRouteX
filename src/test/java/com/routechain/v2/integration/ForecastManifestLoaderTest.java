package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForecastManifestLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsForecastWorkerManifestEntry() throws Exception {
        Path manifestPath = HttpForecastTestSupport.manifest(tempDir, "v1", "sha256:chronos", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("v1", manifest.worker("ml-forecast-worker").modelVersion());
    }
}
