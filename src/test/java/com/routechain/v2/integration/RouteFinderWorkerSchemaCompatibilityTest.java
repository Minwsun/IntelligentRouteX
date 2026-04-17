package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RouteFinderWorkerSchemaCompatibilityTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:8b52802e0e685f7ae36aa62940ca848042ad81341d55dda36190e90a9e7b10fe";

    @TempDir
    Path tempDir;

    @Test
    void incompatibleManifestContractLeavesWorkerNotReady() throws Exception {
        Path manifestPath = HttpRouteFinderTestSupport.manifest(tempDir, "v1", "sha256:routefinder", "dispatch-v2-ml/v2", "dispatch-v2-java/v1");

        HttpRouteFinderClient client = new HttpRouteFinderClient(
                "http://127.0.0.1:65531",
                java.time.Duration.ofMillis(50),
                java.time.Duration.ofMillis(50),
                manifestPath);

        assertFalse(client.readyState().ready());
        assertEquals("ml-contract-incompatible", client.readyState().reason());
    }

    @Test
    void localLoadRequirementFailsWhenWorkerVersionDoesNotReportLoadedLocalModel() throws Exception {
        HttpServer server = HttpRouteFinderTestSupport.server(Map.of(
                "/version", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.versionBody("v1", "sha256:routefinder")),
                "/ready", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.readyBody(true, ""))));
        try {
            Path manifestPath = HttpRouteFinderTestSupport.manifestV2(
                    tempDir,
                    "v1",
                    "sha256:routefinder",
                    "dispatch-v2-ml/v1",
                    "dispatch-v2-java/v1",
                    LOADED_MODEL_FINGERPRINT);

            HttpRouteFinderClient client = new HttpRouteFinderClient(
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
