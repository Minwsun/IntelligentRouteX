package com.routechain.v2.integration;

public record TomTomTrafficRefineResult(
        boolean applied,
        double multiplier,
        long sourceAgeMs,
        double confidence,
        boolean trafficBadSignal,
        long latencyMs,
        String degradeReason) {

    public static TomTomTrafficRefineResult notApplied() {
        return new TomTomTrafficRefineResult(false, 1.0, Long.MAX_VALUE, 0.0, false, 0L, "tomtom-unavailable-or-no-data");
    }
}
