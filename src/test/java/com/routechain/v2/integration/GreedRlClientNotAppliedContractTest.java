package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreedRlClientNotAppliedContractTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:greedrl-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void timeoutMalformedServerErrorAndWorkerFallbackReturnTypedNotAppliedResults() throws Exception {
        assertNotApplied(Map.of(
                "/version", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.versionBody(
                        "v1",
                        "sha256:greedrl",
                        true,
                        "E:/Code _Project/IntelligentRouteX/services/models/materialized/greedrl/model/greedrl-runtime-manifest.json",
                        "LOCAL_PACKAGE_PROMOTION",
                        LOADED_MODEL_FINGERPRINT)),
                "/ready", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.readyBody(true, "")),
                "/bundle/propose", HttpGreedRlTestSupport.delayed(Duration.ofMillis(150), HttpGreedRlTestSupport.bundleResponseBody(false))));
        assertNotApplied(Map.of(
                "/version", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.versionBody(
                        "v1",
                        "sha256:greedrl",
                        true,
                        "E:/Code _Project/IntelligentRouteX/services/models/materialized/greedrl/model/greedrl-runtime-manifest.json",
                        "LOCAL_PACKAGE_PROMOTION",
                        LOADED_MODEL_FINGERPRINT)),
                "/ready", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.readyBody(true, "")),
                "/bundle/propose", HttpGreedRlTestSupport.json("{\"bad\":true}")));
        assertNotApplied(Map.of(
                "/version", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.versionBody(
                        "v1",
                        "sha256:greedrl",
                        true,
                        "E:/Code _Project/IntelligentRouteX/services/models/materialized/greedrl/model/greedrl-runtime-manifest.json",
                        "LOCAL_PACKAGE_PROMOTION",
                        LOADED_MODEL_FINGERPRINT)),
                "/ready", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.readyBody(true, "")),
                "/bundle/propose", HttpGreedRlTestSupport.status(500, "{\"error\":\"boom\"}")));
        assertNotApplied(Map.of(
                "/version", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.versionBody(
                        "v1",
                        "sha256:greedrl",
                        true,
                        "E:/Code _Project/IntelligentRouteX/services/models/materialized/greedrl/model/greedrl-runtime-manifest.json",
                        "LOCAL_PACKAGE_PROMOTION",
                        LOADED_MODEL_FINGERPRINT)),
                "/ready", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.readyBody(true, "")),
                "/bundle/propose", HttpGreedRlTestSupport.json(HttpGreedRlTestSupport.bundleResponseBody(true))));
    }

    private void assertNotApplied(Map<String, com.sun.net.httpserver.HttpHandler> handlers) throws Exception {
        HttpServer server = HttpGreedRlTestSupport.server(handlers);
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
                    Duration.ofMillis(50),
                    Duration.ofMillis(60),
                    manifestPath);

            GreedRlBundleResult result = client.proposeBundles(bundleFeatureVector(), 50L);

            assertFalse(result.applied());
            assertTrue(result.fallbackUsed());
            assertTrue(!result.degradeReason().isBlank());
        } finally {
            server.stop(0);
        }
    }

    private GreedRlBundleFeatureVector bundleFeatureVector() {
        return new GreedRlBundleFeatureVector(
                "greedrl-bundle-feature-vector/v1",
                "trace-greedrl",
                "cluster-1",
                List.of("order-1", "order-2", "order-3"),
                List.of("order-1", "order-2", "order-3"),
                List.of("order-3"),
                Map.of("order-1", 0.8, "order-2", 0.7, "order-3", 0.6),
                3,
                2);
    }
}
