package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GreedRlWorkerSchemaCompatibilityTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:greedrl-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void incompatibleManifestContractLeavesWorkerNotReady() throws Exception {
        Path manifestPath = HttpGreedRlTestSupport.manifest(tempDir, "v1", "sha256:greedrl", "dispatch-v2-ml/v2", "dispatch-v2-java/v1");

        HttpGreedRlClient client = new HttpGreedRlClient(
                "http://127.0.0.1:65531",
                java.time.Duration.ofMillis(50),
                java.time.Duration.ofMillis(50),
                manifestPath);

        assertFalse(client.readyState().ready());
        assertEquals("ml-contract-incompatible", client.readyState().reason());
    }

    @Test
    void localLoadRequirementFailsWhenWorkerVersionDoesNotReportLoadedLocalModel() throws Exception {
        com.sun.net.httpserver.HttpServer server = HttpGreedRlTestSupport.server(java.util.Map.of(
                "/version", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.versionBody("v1", "sha256:greedrl")),
                "/ready", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.readyBody(true, ""))));
        try {
            Path manifestPath = HttpGreedRlTestSupport.manifestV2(
                    tempDir,
                    "v1",
                    "sha256:greedrl",
                    "dispatch-v2-ml/v1",
                    "dispatch-v2-java/v1",
                    LOADED_MODEL_FINGERPRINT);

            HttpGreedRlClient client = new HttpGreedRlClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    java.time.Duration.ofMillis(50),
                    java.time.Duration.ofMillis(50),
                    manifestPath);

            assertFalse(client.readyState().ready());
            assertEquals("local-model-not-loaded", client.readyState().reason());
        } finally {
            server.stop(0);
        }
    }
}
