package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TabularWorkerSchemaCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void incompatibleManifestContractLeavesWorkerNotReady() throws Exception {
        Path manifestPath = HttpTabularTestSupport.manifestV1(tempDir, "v1", "sha256:test", "dispatch-v2-ml/v2", "dispatch-v2-java/v1");

        HttpTabularScoringClient client = new HttpTabularScoringClient(
                "http://127.0.0.1:65530",
                java.time.Duration.ofMillis(50),
                java.time.Duration.ofMillis(50),
                manifestPath);

        assertFalse(client.readyState().ready());
        assertEquals("ml-contract-incompatible", client.readyState().reason());
    }

    @Test
    void workerThatDoesNotReportLocalLoadTruthIsRejectedWhenManifestRequiresIt() throws Exception {
        HttpServer server = HttpTabularTestSupport.server(java.util.Map.of(
                "/version", HttpTabularTestSupport.json(HttpTabularTestSupport.versionBody(
                        "v1",
                        "sha256:test",
                        false,
                        "",
                        "",
                        "")),
                "/ready", HttpTabularTestSupport.json(HttpTabularTestSupport.readyBody(true, ""))));
        try {
            Path manifestPath = HttpTabularTestSupport.manifestV2(
                    tempDir,
                    "v1",
                    "sha256:test",
                    "dispatch-v2-ml/v1",
                    "dispatch-v2-java/v1",
                    "sha256:fingerprint");

            HttpTabularScoringClient client = new HttpTabularScoringClient(
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
