package com.routechain.v2.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.v2.context.EtaEstimateRequest;
import com.routechain.v2.context.TrafficRefineMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class HttpTomTomTrafficRefineClient implements TomTomTrafficRefineClient {
    private static final String FLOW_SEGMENT_PATH = "traffic/services/4/flowSegmentData/absolute/10/json";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String apiKey;
    private final Duration readTimeout;
    private final TrafficRefineMapper trafficRefineMapper;

    public HttpTomTomTrafficRefineClient(String baseUrl,
                                         String apiKey,
                                         Duration connectTimeout,
                                         Duration readTimeout,
                                         TrafficRefineMapper trafficRefineMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.readTimeout = readTimeout;
        this.trafficRefineMapper = trafficRefineMapper;
    }

    @Override
    public TomTomTrafficRefineResult refine(EtaEstimateRequest request, double baselineMinutes, double distanceKm) {
        if (request == null || request.from() == null || request.to() == null) {
            return TomTomTrafficRefineResult.notApplied();
        }
        if (apiKey.isBlank()) {
            return new TomTomTrafficRefineResult(false, 1.0, Long.MAX_VALUE, 0.0, false, 0L, "tomtom-unavailable");
        }
        long startedAt = System.nanoTime();
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(buildFlowSegmentUri(request))
                    .timeout(readTimeout)
                    .GET();
            if (request.traceId() != null && !request.traceId().isBlank()) {
                requestBuilder.header("Tracking-ID", request.traceId());
            }
            HttpRequest httpRequest = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable(mapHttpStatus(response.statusCode()), startedAt);
            }
            TomTomFlowSegmentDataResponse refineResponse = objectMapper.readValue(response.body(), TomTomFlowSegmentDataResponse.class);
            TomTomFlowSegmentDataResponse.FlowSegmentData flowSegmentData = refineResponse == null ? null : refineResponse.flowSegmentData();
            if (flowSegmentData == null
                    || invalidTravelTime(flowSegmentData.currentTravelTime())
                    || invalidTravelTime(flowSegmentData.freeFlowTravelTime())) {
                return unavailable("tomtom-unavailable-or-no-data", startedAt);
            }
            return trafficRefineMapper.map(
                    flowSegmentData.currentTravelTime(),
                    flowSegmentData.freeFlowTravelTime(),
                    0L,
                    safeConfidence(flowSegmentData.confidence()),
                    Boolean.TRUE.equals(flowSegmentData.roadClosure()),
                    latencyMs(startedAt),
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

    private URI buildFlowSegmentUri(EtaEstimateRequest request) {
        double pointLatitude = (request.from().latitude() + request.to().latitude()) / 2.0;
        double pointLongitude = (request.from().longitude() + request.to().longitude()) / 2.0;
        String query = "point=%s&unit=KMPH&key=%s".formatted(
                encode(pointLatitude + "," + pointLongitude),
                encode(apiKey));
        return baseUri.resolve(FLOW_SEGMENT_PATH + "?" + query);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean invalidTravelTime(Double value) {
        return value == null || !Double.isFinite(value) || value <= 0.0;
    }

    private double safeConfidence(Double confidence) {
        if (confidence == null || !Double.isFinite(confidence)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private String mapHttpStatus(int statusCode) {
        if (statusCode == 403 || statusCode == 429) {
            return "tomtom-auth-or-quota-failed";
        }
        return "tomtom-http-error";
    }

    private long latencyMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
