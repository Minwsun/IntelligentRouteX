package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForecastClientNotAppliedContractTest {
    private static final String LOADED_MODEL_FINGERPRINT = "sha256:chronos-fingerprint";

    @TempDir
    Path tempDir;

    @Test
    void timeoutMalformedServerErrorAndFallbackReturnTypedNotAppliedResult() throws Exception {
        assertUnavailable(Map.of("/forecast/demand-shift", HttpForecastTestSupport.delayed(Duration.ofMillis(150), HttpForecastTestSupport.forecastBody(false, 0.71, null, 0.8, 90000L))));
        assertUnavailable(Map.of("/forecast/demand-shift", HttpForecastTestSupport.json("{\"bad\":true}")));
        assertUnavailable(Map.of("/forecast/demand-shift", HttpForecastTestSupport.status(500, "{\"error\":\"boom\"}")));
        assertUnavailable(Map.of("/forecast/demand-shift", HttpForecastTestSupport.json(HttpForecastTestSupport.forecastBody(true, 0.71, null, 0.8, 90000L))));
    }

    private void assertUnavailable(Map<String, com.sun.net.httpserver.HttpHandler> handlers) throws Exception {
        HttpServer server = HttpForecastTestSupport.server(mergeHandlers(handlers));
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
                    Duration.ofMillis(60),
                    manifestPath);

            ForecastResult result = client.forecastDemandShift(
                    new DemandShiftFeatureVector("demand-shift-feature-vector/v1", "trace-forecast", "corridor-a", 3, 1, 2, 4.0, 5.0, 16.0, 0.68, 0.2, 30),
                    60L);

            assertFalse(result.applied());
            assertTrue(!result.degradeReason().isBlank());
        } finally {
            server.stop(0);
        }
    }

    private Map<String, com.sun.net.httpserver.HttpHandler> mergeHandlers(Map<String, com.sun.net.httpserver.HttpHandler> handlers) {
        return new java.util.HashMap<>(Map.of(
                "/version", HttpForecastTestSupport.json(HttpForecastTestSupport.versionBody(
                        "v1",
                        "sha256:chronos",
                        true,
                        "materialized/chronos-2/model/chronos-runtime-manifest.json",
                        "HF_SNAPSHOT_PROMOTION",
                        LOADED_MODEL_FINGERPRINT)),
                "/ready", HttpForecastTestSupport.json(HttpForecastTestSupport.readyBody(true, "")))) {{
            putAll(handlers);
        }};
    }
}
