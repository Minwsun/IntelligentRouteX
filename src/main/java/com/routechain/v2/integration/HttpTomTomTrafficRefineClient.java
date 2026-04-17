package com.routechain.v2.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.v2.context.EtaEstimateRequest;
import com.routechain.v2.context.TrafficRefineMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpTomTomTrafficRefineClient implements TomTomTrafficRefineClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final Duration readTimeout;
    private final TrafficRefineMapper trafficRefineMapper;

    public HttpTomTomTrafficRefineClient(String baseUrl,
                                         Duration connectTimeout,
                                         Duration readTimeout,
                                         TrafficRefineMapper trafficRefineMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.readTimeout = readTimeout;
        this.trafficRefineMapper = trafficRefineMapper;
    }

    @Override
    public TomTomTrafficRefineResult refine(EtaEstimateRequest request, double baselineMinutes, double distanceKm) {
        if (request == null || request.from() == null || request.to() == null) {
            return TomTomTrafficRefineResult.notApplied();
        }
        long startedAt = System.nanoTime();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("traffic/refine"))
                    .header("Content-Type", "application/json")
                    .timeout(readTimeout)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(new TomTomTrafficRefineRequest(
                            "tomtom-traffic-refine-request/v1",
                            request.traceId(),
                            request.from().latitude(),
                            request.from().longitude(),
                            request.to().latitude(),
                            request.to().longitude(),
                            request.decisionTime() == null ? 0L : request.decisionTime().toEpochMilli(),
                            baselineMinutes,
                            distanceKm))))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable("tomtom-http-error", startedAt);
            }
            TomTomTrafficRefineResponse refineResponse = objectMapper.readValue(response.body(), TomTomTrafficRefineResponse.class);
            if (refineResponse.fallbackUsed()) {
                return unavailable("tomtom-worker-fallback", startedAt);
            }
            return trafficRefineMapper.map(
                    refineResponse.multiplier(),
                    refineResponse.sourceAgeMs(),
                    refineResponse.confidence(),
                    refineResponse.trafficBadSignal(),
                    refineResponse.latencyMs(),
                    "");
        } catch (java.net.http.HttpTimeoutException exception) {
            return unavailable("tomtom-timeout", startedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable("tomtom-interrupted", startedAt);
        } catch (IOException | RuntimeException exception) {
            return unavailable("tomtom-unavailable", startedAt);
        }
    }

    private TomTomTrafficRefineResult unavailable(String degradeReason, long startedAt) {
        return new TomTomTrafficRefineResult(false, 1.0, Long.MAX_VALUE, 0.0, false, latencyMs(startedAt), degradeReason);
    }

    private long latencyMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
