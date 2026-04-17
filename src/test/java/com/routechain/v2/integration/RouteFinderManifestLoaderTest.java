package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteFinderManifestLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRouteFinderManifestEntry() throws Exception {
        Path manifestPath = HttpRouteFinderTestSupport.manifest(tempDir, "v1", "sha256:routefinder", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("model-manifest/v1", manifest.schemaVersion());
        assertEquals("v1", manifest.worker("ml-routefinder-worker").modelVersion());
    }
}
