package com.routechain.v2.integration;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFinderWorkerVersionTest {
    private static final String LOCAL_ARTIFACT_PATH = "E:/Code _Project/IntelligentRouteX/services/models/materialized/routefinder/model/routefinder-model.json";
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:8b52802e0e685f7ae36aa62940ca848042ad81341d55dda36190e90a9e7b10fe";

    @TempDir
    Path tempDir;

    @Test
    void readyWorkerExposesPinnedVersionMetadataWithLocalLoadFields() throws Exception {
        WorkerVersionResponse parsed = JsonMapper.builder().findAndAddModules().build().readValue(
                HttpRouteFinderTestSupport.versionBody(
                        "v1",
                        "sha256:routefinder",
                        true,
                        LOCAL_ARTIFACT_PATH,
                        "LOCAL_FILE",
                        LOADED_MODEL_FINGERPRINT),
                WorkerVersionResponse.class);
        assertTrue(Boolean.TRUE.equals(parsed.loadedFromLocal()));
        assertEquals(LOCAL_ARTIFACT_PATH, parsed.localArtifactPath());
        assertEquals("LOCAL_FILE", parsed.materializationMode());
        assertEquals(LOADED_MODEL_FINGERPRINT, parsed.loadedModelFingerprint());

        HttpServer server = HttpRouteFinderTestSupport.server(Map.of(
                "/version", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.versionBody(
                        "v1",
                        "sha256:routefinder",
                        true,
                        LOCAL_ARTIFACT_PATH,
                        "LOCAL_FILE",
                        LOADED_MODEL_FINGERPRINT)),
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

            assertTrue(client.readyState().ready());
            assertEquals("v1", client.readyState().workerMetadata().modelVersion());
            assertEquals("sha256:routefinder", client.readyState().workerMetadata().artifactDigest());
        } finally {
            server.stop(0);
        }
    }
}
