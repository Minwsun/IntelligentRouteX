package com.routechain.v2.integration;

public record OpenMeteoSnapshot(
        boolean available,
        String weatherCondition,
        double multiplier,
        boolean weatherBadSignal,
        long sourceAgeMs,
        double confidence,
        long latencyMs,
        String degradeReason) {

    public static OpenMeteoSnapshot unavailable() {
        return new OpenMeteoSnapshot(false, "unknown", 1.0, false, Long.MAX_VALUE, 0.0, 0L, "open-meteo-unavailable");
    }
}
