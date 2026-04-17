package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedRlWorkerVersionTest {

    @TempDir
    Path tempDir;

    @Test
    void readyWorkerExposesPinnedVersionMetadata() throws Exception {
        HttpServer server = HttpGreedRlTestSupport.server(Map.of(
                "/version", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.versionBody("v1", "sha256:greedrl")),
                "/ready", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.readyBody(true, ""))));
        try {
            Path manifestPath = HttpGreedRlTestSupport.manifest(tempDir, "v1", "sha256:greedrl", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");
            HttpGreedRlClient client = new HttpGreedRlClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(50),
                    manifestPath);

            assertTrue(client.readyState().ready());
            assertEquals("v1", client.readyState().workerMetadata().modelVersion());
            assertEquals("sha256:greedrl", client.readyState().workerMetadata().artifactDigest());
        } finally {
            server.stop(0);
        }
    }
}
