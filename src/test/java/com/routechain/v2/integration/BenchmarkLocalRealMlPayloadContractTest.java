package com.routechain.v2.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import com.routechain.v2.perf.DispatchPerfWorkloadFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkLocalRealMlPayloadContractTest {
    private static final ObjectMapper STRICT_JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void benchmarkLikeDispatchEmitsStrictJsonPayloadsForAllMlWorkers() throws Exception {
        RequestCapture tabularCapture = new RequestCapture();
        RequestCapture routefinderCapture = new RequestCapture();
        RequestCapture greedrlCapture = new RequestCapture();
        RequestCapture forecastCapture = new RequestCapture();

        HttpServer tabularServer = server(Map.of(
                "/version", json(HttpTabularTestSupport.versionBody("v1", "sha256:tabular")),
                "/ready", json(HttpTabularTestSupport.readyBody(true, "")),
                "/score/eta-residual", captureThenJson(tabularCapture, HttpTabularTestSupport.scoreBody(0.2, 0.1)),
                "/score/pair", captureThenJson(tabularCapture, HttpTabularTestSupport.scoreBody(0.2, 0.1)),
                "/score/driver-fit", captureThenJson(tabularCapture, HttpTabularTestSupport.scoreBody(0.2, 0.1)),
                "/score/route-value", captureThenJson(tabularCapture, HttpTabularTestSupport.scoreBody(0.2, 0.1))));
        HttpServer routefinderServer = server(Map.of(
                "/version", json(HttpRouteFinderTestSupport.versionBody("v1", "sha256:routefinder")),
                "/ready", json(HttpRouteFinderTestSupport.readyBody(true, "")),
                "/route/refine", captureThenJson(routefinderCapture, HttpRouteFinderTestSupport.routeBody("routefinder-refined")),
                "/route/alternatives", captureThenJson(routefinderCapture, HttpRouteFinderTestSupport.routeBody("routefinder-alternative"))));
        HttpServer greedrlServer = server(Map.of(
                "/version", json(HttpGreedRlTestSupport.versionBody("v1", "sha256:greedrl")),
                "/ready", json(HttpGreedRlTestSupport.readyBody(true, "")),
                "/bundle/propose", captureThenJson(greedrlCapture, HttpGreedRlTestSupport.bundleResponseBody(false)),
                "/sequence/propose", captureThenJson(greedrlCapture, HttpGreedRlTestSupport.sequenceResponseBody(false))));
        HttpServer forecastServer = server(Map.of(
                "/version", json(HttpForecastTestSupport.versionBody("v1", "sha256:forecast")),
                "/ready", json(HttpForecastTestSupport.readyBody(true, "")),
                "/forecast/demand-shift", captureThenJson(forecastCapture, HttpForecastTestSupport.forecastBody(false, 0.71, null, 0.83, 90000L)),
                "/forecast/zone-burst", captureThenJson(forecastCapture, HttpForecastTestSupport.forecastBody(false, null, 0.74, 0.82, 80000L)),
                "/forecast/post-drop-shift", captureThenJson(forecastCapture, HttpForecastTestSupport.forecastBody(false, 0.69, null, 0.80, 85000L))));
        try {
            Path manifestPath = combinedManifest(tempDir);
            RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
            properties.setEnabled(true);
            properties.setMlEnabled(true);
            properties.getMl().setModelManifestPath(manifestPath.toString());
            properties.getMl().getTabular().setEnabled(true);
            properties.getMl().getTabular().setBaseUrl(baseUrl(tabularServer));
            properties.getMl().getTabular().setConnectTimeout(Duration.ofMillis(50));
            properties.getMl().getTabular().setReadTimeout(Duration.ofMillis(150));
            properties.getMl().getRoutefinder().setEnabled(true);
            properties.getMl().getRoutefinder().setBaseUrl(baseUrl(routefinderServer));
            properties.getMl().getRoutefinder().setConnectTimeout(Duration.ofMillis(50));
            properties.getMl().getRoutefinder().setReadTimeout(Duration.ofMillis(150));
            properties.getMl().getGreedrl().setEnabled(true);
            properties.getMl().getGreedrl().setBaseUrl(baseUrl(greedrlServer));
            properties.getMl().getGreedrl().setConnectTimeout(Duration.ofMillis(50));
            properties.getMl().getGreedrl().setReadTimeout(Duration.ofMillis(150));
            properties.getMl().getForecast().setEnabled(true);
            properties.getMl().getForecast().setBaseUrl(baseUrl(forecastServer));
            properties.getMl().getForecast().setConnectTimeout(Duration.ofMillis(50));
            properties.getMl().getForecast().setReadTimeout(Duration.ofMillis(200));

            HttpTabularScoringClient tabularClient = new HttpTabularScoringClient(
                    properties.getMl().getTabular().getBaseUrl(),
                    properties.getMl().getTabular().getConnectTimeout(),
                    properties.getMl().getTabular().getReadTimeout(),
                    manifestPath);
            HttpRouteFinderClient routeFinderClient = new HttpRouteFinderClient(
                    properties.getMl().getRoutefinder().getBaseUrl(),
                    properties.getMl().getRoutefinder().getConnectTimeout(),
                    properties.getMl().getRoutefinder().getReadTimeout(),
                    manifestPath);
            HttpGreedRlClient greedRlClient = new HttpGreedRlClient(
                    properties.getMl().getGreedrl().getBaseUrl(),
                    properties.getMl().getGreedrl().getConnectTimeout(),
                    properties.getMl().getGreedrl().getReadTimeout(),
                    manifestPath);
            HttpForecastClient forecastClient = new HttpForecastClient(
                    properties.getMl().getForecast().getBaseUrl(),
                    properties.getMl().getForecast().getConnectTimeout(),
                    properties.getMl().getForecast().getReadTimeout(),
                    manifestPath);

            DispatchV2Result result = TestDispatchV2Factory.harness(
                    properties,
                    tabularClient,
                    routeFinderClient,
                    greedRlClient,
                    forecastClient,
                    new NoOpOpenMeteoClient(),
                    new NoOpTomTomTrafficRefineClient())
                    .core()
                    .dispatch(DispatchPerfWorkloadFactory.request(
                            DispatchPerfBenchmarkHarness.WorkloadSize.S,
                            "quality-payload-contract"));

            assertFalse(result.mlStageMetadata().isEmpty());
            assertFalse(tabularCapture.requests().isEmpty(), "tabular client should emit requests");
            assertFalse(routefinderCapture.requests().isEmpty(), "routefinder client should emit requests");
            assertFalse(greedrlCapture.requests().isEmpty(), "greedrl client should emit requests");
            assertFalse(forecastCapture.requests().isEmpty(), "forecast client should emit requests");
            assertStrictJson(tabularCapture.requests());
            assertStrictJson(routefinderCapture.requests());
            assertStrictJson(greedrlCapture.requests());
            assertStrictJson(forecastCapture.requests());
        } finally {
            tabularServer.stop(0);
            routefinderServer.stop(0);
            greedrlServer.stop(0);
            forecastServer.stop(0);
        }
    }

    private static void assertStrictJson(List<RequestRecord> requests) throws IOException {
        for (RequestRecord request : requests) {
            STRICT_JSON.readTree(request.body());
            assertFalse(request.body().contains(":NaN"), () -> "non-finite NaN leaked into JSON: " + request.body());
            assertFalse(request.body().contains(":Infinity"), () -> "non-finite Infinity leaked into JSON: " + request.body());
            assertFalse(request.body().contains(":-Infinity"), () -> "non-finite -Infinity leaked into JSON: " + request.body());
            assertFalse(request.headers().containsKey("Upgrade"), () -> "unexpected h2c upgrade header: " + request.headers());
            assertFalse(request.headers().containsKey("HTTP2-Settings"), () -> "unexpected HTTP2-Settings header: " + request.headers());
            String connectionHeader = String.join(",", request.headers().getOrDefault("Connection", List.of()));
            assertFalse(connectionHeader.toLowerCase().contains("upgrade"),
                    () -> "unexpected connection upgrade header: " + request.headers());
        }
    }

    private static HttpServer server(Map<String, HttpHandler> handlers) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (Map.Entry<String, HttpHandler> entry : handlers.entrySet()) {
            server.createContext(entry.getKey(), entry.getValue());
        }
        server.start();
        return server;
    }

    private static HttpHandler json(String body) {
        return exchange -> write(exchange, 200, body);
    }

    private static HttpHandler captureThenJson(RequestCapture capture, String body) {
        return exchange -> {
            capture.requests().add(new RequestRecord(
                    Map.copyOf(exchange.getRequestHeaders()),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            write(exchange, 200, body);
        };
    }

    private static void write(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Path combinedManifest(Path root) throws IOException {
        Path manifestPath = root.resolve("model-manifest.yaml");
        Files.writeString(manifestPath, """
                schemaVersion: model-manifest/v2
                workers:
                  - worker_name: ml-tabular-worker
                    model_name: tabular-test
                    model_version: v1
                    artifact_digest: sha256:tabular
                    rollback_artifact_digest: sha256:rollback
                    runtime_image: local/test
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                  - worker_name: ml-routefinder-worker
                    model_name: routefinder-test
                    model_version: v1
                    artifact_digest: sha256:routefinder
                    rollback_artifact_digest: sha256:rollback
                    runtime_image: local/test
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                  - worker_name: ml-greedrl-worker
                    model_name: greedrl-test
                    model_version: v1
                    artifact_digest: sha256:greedrl
                    rollback_artifact_digest: sha256:rollback
                    runtime_image: local/test
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                  - worker_name: ml-forecast-worker
                    model_name: forecast-test
                    model_version: v1
                    artifact_digest: sha256:forecast
                    rollback_artifact_digest: sha256:rollback
                    runtime_image: local/test
                    compatibility_contract_version: dispatch-v2-ml/v1
                    min_supported_java_contract_version: dispatch-v2-java/v1
                """, StandardCharsets.UTF_8);
        return manifestPath;
    }

    private record RequestCapture(List<RequestRecord> requests) {
        private RequestCapture() {
            this(new ArrayList<>());
        }
    }

    private record RequestRecord(Map<String, List<String>> headers, String body) {
    }
}
