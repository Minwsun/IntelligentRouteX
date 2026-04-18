package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ForecastWorkerSchemaCompatibilityTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:chronos-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void incompatibleManifestContractLeavesWorkerNotReady() throws Exception {
        Path manifestPath = HttpForecastTestSupport.manifest(tempDir, "v1", "sha256:chronos", "dispatch-v2-ml/v2", "dispatch-v2-java/v1");

        HttpForecastClient client = new HttpForecastClient(
                "http://127.0.0.1:65531",
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                manifestPath);

        assertFalse(client.readyState().ready());
        assertEquals("ml-contract-incompatible", client.readyState().reason());
    }

    @Test
    void localLoadRequirementFailsWhenWorkerVersionDoesNotReportLoadedLocalModel() throws Exception {
        com.sun.net.httpserver.HttpServer server = HttpForecastTestSupport.server(java.util.Map.of(
                "/version", HttpForecastTestSupport.json(HttpForecastTestSupport.versionBody("v1", "sha256:chronos")),
                "/ready", HttpForecastTestSupport.json(HttpForecastTestSupport.readyBody(true, ""))));
        try {
            Path manifestPath = HttpForecastTestSupport.manifestV2(
                    tempDir,
                    "v1",
                    "sha256:chronos",
                    "dispatch-v2-ml/v1",
                    "dispatch-v2-java/v1",
                    LOADED_MODEL_FINGERPRINT);

            HttpForecastClient client = new HttpForecastClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(50),
                    manifestPath);

            assertFalse(client.readyState().ready());
            assertEquals("local-model-not-loaded", client.readyState().reason());
        } finally {
            server.stop(0);
        }
    }
}
