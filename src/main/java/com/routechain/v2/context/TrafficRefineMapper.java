package com.routechain.v2.context;

public final class TrafficRefineMapper {
    private static final double TRAFFIC_BAD_MULTIPLIER_THRESHOLD = 1.22;

    public com.routechain.v2.integration.TomTomTrafficRefineResult map(double currentTravelTimeSeconds,
                                                                       double freeFlowTravelTimeSeconds,
                                                                       long sourceAgeMs,
                                                                       double confidence,
                                                                       boolean roadClosure,
                                                                       long latencyMs,
                                                                       String degradeReason) {
        double safeFreeFlowSeconds = Math.max(0.0001, freeFlowTravelTimeSeconds);
        double multiplier = currentTravelTimeSeconds / safeFreeFlowSeconds;
        boolean trafficBadSignal = roadClosure || multiplier >= TRAFFIC_BAD_MULTIPLIER_THRESHOLD;
        return new com.routechain.v2.integration.TomTomTrafficRefineResult(
                true,
                multiplier,
                sourceAgeMs,
                confidence,
                trafficBadSignal,
                latencyMs,
                degradeReason);
    }
}
