package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteFinderClientNotAppliedContractTest {

    @TempDir
    Path tempDir;

    @Test
    void timeoutMalformedAndServerErrorReturnTypedNotAppliedResults() throws Exception {
        assertNotApplied(Map.of(
                "/version", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.versionBody("v1", "sha256:routefinder")),
                "/ready", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.readyBody(true, "")),
                "/route/alternatives", HttpRouteFinderTestSupport.delayed(Duration.ofMillis(150), HttpRouteFinderTestSupport.routeBody("routefinder-alternative"))));
        assertNotApplied(Map.of(
                "/version", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.versionBody("v1", "sha256:routefinder")),
                "/ready", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.readyBody(true, "")),
                "/route/alternatives", HttpRouteFinderTestSupport.json("{\"bad\":true}")));
        assertNotApplied(Map.of(
                "/version", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.versionBody("v1", "sha256:routefinder")),
                "/ready", HttpRouteFinderTestSupport.json(HttpRouteFinderTestSupport.readyBody(true, "")),
                "/route/alternatives", HttpRouteFinderTestSupport.status(500, "{\"error\":\"boom\"}")));
    }

    private void assertNotApplied(Map<String, com.sun.net.httpserver.HttpHandler> handlers) throws Exception {
        HttpServer server = HttpRouteFinderTestSupport.server(handlers);
        try {
            Path manifestPath = HttpRouteFinderTestSupport.manifest(tempDir, "v1", "sha256:routefinder", "dispatch-v2-ml/v1", "dispatch-v2-java/v1");
            HttpRouteFinderClient client = new HttpRouteFinderClient(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(50),
                    Duration.ofMillis(60),
                    manifestPath);

            RouteFinderResult result = client.generateAlternatives(featureVector(), 50L);

            assertFalse(result.applied());
            assertTrue(result.fallbackUsed());
            assertTrue(!result.degradeReason().isBlank());
        } finally {
            server.stop(0);
        }
    }

    private RouteFinderFeatureVector featureVector() {
        return new RouteFinderFeatureVector(
                "routefinder-feature-vector/v1",
                "trace-test",
                "bundle-1",
                "order-1",
                "driver-1",
                "HEURISTIC_FAST",
                java.util.List.of("order-1", "order-2", "order-3"),
                java.util.List.of("order-1", "order-2", "order-3"),
                5.0,
                18.0,
                0.7,
                0.8,
                0.75,
                0.65,
                false,
                2);
    }
}
