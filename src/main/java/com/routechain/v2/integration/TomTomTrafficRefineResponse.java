package com.routechain.v2.integration;

public record TomTomTrafficRefineResponse(
        String schemaVersion,
        String traceId,
        boolean fallbackUsed,
        double multiplier,
        long sourceAgeMs,
        double confidence,
        boolean trafficBadSignal,
        long latencyMs) {
}
