package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastWorkerVersionTest {
    private static final String LOCAL_ARTIFACT_PATH = "materialized/chronos-2/model/chronos-runtime-manifest.json";
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:chronos-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void readyWorkerExposesPinnedVersionMetadata() throws Exception {
        WorkerVersionResponse parsed = com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().readValue(
                HttpForecastTestSupport.versionBody(
                        "v1",
                        "sha256:chronos",
                        true,
                        LOCAL_ARTIFACT_PATH,
                        "HF_SNAPSHOT_PROMOTION",
                        LOADED_MODEL_FINGERPRINT,
                        "cuda",
                        true),
                WorkerVersionResponse.class);
        assertTrue(Boolean.TRUE.equals(parsed.loadedFromLocal()));
        assertEquals(LOCAL_ARTIFACT_PATH, parsed.localArtifactPath());
        assertEquals("HF_SNAPSHOT_PROMOTION", parsed.materializationMode());
        assertEquals(LOADED_MODEL_FINGERPRINT, parsed.loadedModelFingerprint());
        assertEquals("cuda", parsed.device());
        assertTrue(Boolean.TRUE.equals(parsed.cudaAvailable()));

        HttpServer server = HttpForecastTestSupport.server(Map.of(
                "/version", HttpForecastTestSupport.json(HttpForecastTestSupport.versionBody(
                        "v1",
                        "sha256:chronos",
                        true,
                        LOCAL_ARTIFACT_PATH,
                        "HF_SNAPSHOT_PROMOTION",
                        LOADED_MODEL_FINGERPRINT,
                        "cuda",
                        true)),
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

            assertTrue(client.readyState().ready());
            assertEquals("v1", client.readyState().workerMetadata().modelVersion());
            assertEquals("sha256:chronos", client.readyState().workerMetadata().artifactDigest());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void bootstrapReadyStateAllowsSlowPortableForecastWorker() throws Exception {
        HttpServer server = HttpForecastTestSupport.server(Map.of(
                "/version", HttpForecastTestSupport.json(HttpForecastTestSupport.versionBody(
                        "v1",
                        "sha256:chronos",
                        true,
                        LOCAL_ARTIFACT_PATH,
                        "HF_SNAPSHOT_PROMOTION",
                        LOADED_MODEL_FINGERPRINT,
                        "cuda",
                        true)),
                "/ready", HttpForecastTestSupport.delayed(Duration.ofSeconds(1), HttpForecastTestSupport.readyBody(true, ""))));
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

            assertTrue(client.readyState().ready());
            assertEquals("v1", client.readyState().workerMetadata().modelVersion());
            assertEquals("sha256:chronos", client.readyState().workerMetadata().artifactDigest());
        } finally {
            server.stop(0);
        }
    }
}
