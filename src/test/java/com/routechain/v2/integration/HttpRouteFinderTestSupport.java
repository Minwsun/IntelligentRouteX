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

final class HttpRouteFinderTestSupport {
    private HttpRouteFinderTestSupport() {
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
        Path manifestPath = tempDir.resolve("routefinder-model-manifest.yaml");
        Files.writeString(manifestPath, """
                schemaVersion: model-manifest/v1
                workers:
                  - worker_name: ml-routefinder-worker
                    model_name: routefinder-local
                    model_version: %s
                    artifact_digest: %s
                    rollback_artifact_digest: sha256:rollback
                    runtime_image: local/test
                    compatibility_contract_version: %s
                    min_supported_java_contract_version: %s
                    startup_warmup_request:
                      endpoint: /route/refine
                      payload:
                        schemaVersion: route-request/v1
                        traceId: warmup-routefinder
                        payload:
                          schemaVersion: routefinder-feature-vector/v1
                          traceId: warmup-routefinder
                          bundleId: bundle-1
                          anchorOrderId: order-1
                          driverId: driver-1
                          baselineSource: HEURISTIC_FAST
                          baselineStopOrder:
                            - order-1
                            - order-2
                            - order-3
                          bundleOrderIds:
                            - order-1
                            - order-2
                            - order-3
                          projectedPickupEtaMinutes: 5.0
                          projectedCompletionEtaMinutes: 18.0
                          rerankScore: 0.7
                          bundleScore: 0.8
                          anchorScore: 0.75
                          averagePairSupport: 0.65
                          boundaryCross: false
                          maxAlternatives: 2
                """.formatted(modelVersion, artifactDigest, compatibilityContractVersion, javaContractVersion), StandardCharsets.UTF_8);
        return manifestPath;
    }

    static String versionBody(String modelVersion, String artifactDigest) {
        return """
                {
                  "schemaVersion": "worker-version/v1",
                  "worker": "ml-routefinder-worker",
                  "model": "routefinder-local",
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

    static String routeBody(String reason) {
        return """
                {
                  "schemaVersion": "routefinder-response/v1",
                  "traceId": "trace-test",
                  "sourceModel": "routefinder-local",
                  "modelVersion": "v1",
                  "artifactDigest": "sha256:routefinder",
                  "latencyMs": 7,
                  "fallbackUsed": false,
                  "payload": {
                    "routes": [
                      {
                        "stopOrder": ["order-1", "order-3", "order-2"],
                        "projectedPickupEtaMinutes": 4.5,
                        "projectedCompletionEtaMinutes": 16.5,
                        "routeScore": 0.77,
                        "traceReasons": ["%s"]
                      }
                    ]
                  }
                }
                """.formatted(reason);
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
