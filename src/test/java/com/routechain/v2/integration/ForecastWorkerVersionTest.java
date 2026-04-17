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

    @TempDir
    Path tempDir;

    @Test
    void readyWorkerExposesPinnedVersionMetadata() throws Exception {
        HttpServer server = HttpForecastTestSupport.server(Map.of(
                "/version", HttpForecastTestSupport.json(HttpForecastTestSupport.versionBody("v1", "sha256:chronos")),
                "/ready", HttpForecastTestSupport.json(HttpForecastTestSupport.readyBody(true, ""))));
        try {
            Path manifestPath = HttpForecastTestSupport.manifest(tempDir, "v1", "sha256:chronos", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");
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
