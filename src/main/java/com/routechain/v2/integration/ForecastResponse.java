package com.routechain.v2.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ForecastResponse(
        String schemaVersion,
        String traceId,
        String sourceModel,
        @JsonProperty("modelVersion")
        String modelVersion,
        @JsonProperty("artifactDigest")
        String artifactDigest,
        @JsonProperty("latencyMs")
        long latencyMs,
        @JsonProperty("fallbackUsed")
        boolean fallbackUsed,
        ForecastPayload payload) {

    public record ForecastPayload(
            @JsonProperty("horizonMinutes")
            int horizonMinutes,
            @JsonProperty("shiftProbability")
            Double shiftProbability,
            @JsonProperty("burstProbability")
            Double burstProbability,
            Map<String, Double> quantiles,
            double confidence,
            @JsonProperty("sourceAgeMs")
            long sourceAgeMs) {
    }
}
