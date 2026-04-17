package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import com.routechain.v2.context.EtaFeatureVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabularClientNotAppliedContractTest {

    @TempDir
    Path tempDir;

    @Test
    void timeoutMalformedAndServerErrorReturnTypedNotAppliedResults() throws Exception {
        assertNotApplied(Map.of(
                "/version", HttpTabularTestSupport.json(HttpTabularTestSupport.versionBody("v1", "sha256:test")),
                "/ready", HttpTabularTestSupport.json(HttpTabularTestSupport.readyBody(true, "")),
                "/score/eta-residual", HttpTabularTestSupport.delayed(Duration.ofMillis(150), HttpTabularTestSupport.scoreBody(0.2, 0.1))));
        assertNotApplied(Map.of(
                "/version", HttpTabularTestSupport.json(HttpTabularTestSupport.versionBody("v1", "sha256:test")),
                "/ready", HttpTabularTestSupport.json(HttpTabularTestSupport.readyBody(true, "")),
                "/score/eta-residual", HttpTabularTestSupport.json("{\"bad\":true}")));
        assertNotApplied(Map.of(
                "/version", HttpTabularTestSupport.json(HttpTabularTestSupport.versionBody("v1", "sha256:test")),
                "/ready", HttpTabularTestSupport.json(HttpTabularTestSupport.readyBody(true, "")),
                "/score/eta-residual", HttpTabularTestSupport.status(500, "{\"error\":\"boom\"}")));
    }

    private void assertNotApplied(Map<String, com.sun.net.httpserver.HttpHandler> handlers) throws Exception {
        HttpServer server = HttpTabularTestSupport.server(handlers);
        try {
            Path manifestPath = HttpTabularTestSupport.manifest(tempDir, "v1", "sha256:test", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");
            HttpTabularScoringClient client = new HttpTabularScoringClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(60),
                    manifestPath);

            TabularScoreResult result = client.scoreEtaResidual(new EtaFeatureVector("eta-feature-vector/v1", 5.0, 1.0, 1.0, 2.0, 12), 50L);

            assertFalse(result.applied());
            assertTrue(result.fallbackUsed());
            assertTrue(!result.degradeReason().isBlank());
        } finally {
            server.stop(0);
        }
    }
}
