package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

final class HttpForecastTestSupport {
    private HttpForecastTestSupport() {
    }

    static HttpServer server(Map<String, HttpHandler> handlers) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (Map.Entry<String, HttpHandler> entry : handlers.entrySet()) {
            server.createContext(entry.getKey(), entry.getValue());
        }
        server.start();
        return server;
    }

    static HttpHandler json(String body) {
        return exchange -> write(exchange, 200, body);
    }

    static HttpHandler status(int statusCode, String body) {
        return exchange -> write(exchange, statusCode, body);
    }

    static HttpHandler delayed(Duration delay, String body) {
        return exchange -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, body);
        };
    }

    static Path manifest(Path tempDir,
                         String modelVersion,
                         String artifactDigest,
                         String compatibilityContractVersion,
                         String javaContractVersion) throws IOException {
        Path manifestPath = tempDir.resolve("forecast-model-manifest.yaml");
        Files.writeString(manifestPath, """
                schemaVersion: model-manifest/v1
                workers:
                  - worker_name: ml-forecast-worker
                    model_name: chronos-2
                    model_version: %s
                    artifact_digest: %s
                    rollback_artifact_digest: sha256:rollback
                    runtime_image: local/test
                    compatibility_contract_version: %s
                    min_supported_java_contract_version: %s
                    startup_warmup_request:
                      endpoint: /forecast/zone-burst
                      payload:
                        schemaVersion: forecast-request/v1
                        traceId: warmup-forecast
                        payload:
                          schemaVersion: zone-burst-feature-vector/v1
                          traceId: warmup-forecast
                          corridorId: corridor-a
                          orderCount: 3
                          urgentOrderCount: 1
                          driverCount: 2
                          averageCompletionEtaMinutes: 16.0
                          averageRouteValue: 0.68
                          averageBundleScore: 0.7
                          averagePairSupport: 0.64
                          horizonMinutes: 20
                """.formatted(modelVersion, artifactDigest, compatibilityContractVersion, javaContractVersion), StandardCharsets.UTF_8);
        return manifestPath;
    }

    static String versionBody(String modelVersion, String artifactDigest) {
        return """
                {
                  "schemaVersion": "worker-version/v1",
                  "worker": "ml-forecast-worker",
                  "model": "chronos-2",
                  "modelVersion": "%s",
                  "artifactDigest": "%s",
                  "compatibilityContractVersion": "dispatch-v2-ml/v1",
                  "minSupportedJavaContractVersion": "dispatch-v2-java/v1"
                }
                """.formatted(modelVersion, artifactDigest);
    }

    static String readyBody(boolean ready, String reason) {
        return """
                {
                  "schemaVersion": "worker-ready/v1",
                  "ready": %s,
                  "reason": "%s"
                }
                """.formatted(Boolean.toString(ready), reason);
    }

    static String forecastBody(boolean fallbackUsed, Double shiftProbability, Double burstProbability, double confidence, long sourceAgeMs) {
        return """
                {
                  "schemaVersion": "forecast-response/v1",
                  "traceId": "trace-forecast",
                  "sourceModel": "chronos-2",
                  "modelVersion": "v1",
                  "artifactDigest": "sha256:chronos",
                  "latencyMs": 11,
                  "fallbackUsed": %s,
                  "payload": {
                    "horizonMinutes": 30,
                    "shiftProbability": %s,
                    "burstProbability": %s,
                    "quantiles": {"q10": -0.10, "q50": 0.12, "q90": 0.24},
                    "confidence": %s,
                    "sourceAgeMs": %s
                  }
                }
                """.formatted(
                Boolean.toString(fallbackUsed),
                shiftProbability == null ? "null" : shiftProbability.toString(),
                burstProbability == null ? "null" : burstProbability.toString(),
                Double.toString(confidence),
                Long.toString(sourceAgeMs));
    }

    private static void write(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
}
