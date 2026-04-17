package com.routechain.v2.context;

public final class TrafficRefineMapper {
    public com.routechain.v2.integration.TomTomTrafficRefineResult map(double multiplier,
                                                                       long sourceAgeMs,
                                                                       double confidence,
                                                                       boolean trafficBadSignal,
                                                                       long latencyMs,
                                                                       String degradeReason) {
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
