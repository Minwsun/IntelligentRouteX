package com.routechain.v2.integration;

import com.routechain.v2.context.EtaEstimateRequest;

import java.util.function.Function;

public final class TestTomTomTrafficRefineClient implements TomTomTrafficRefineClient {
    private final Function<EtaEstimateRequest, TomTomTrafficRefineResult> function;

    public TestTomTomTrafficRefineClient(Function<EtaEstimateRequest, TomTomTrafficRefineResult> function) {
        this.function = function;
    }

    public static TestTomTomTrafficRefineClient applied(double multiplier, boolean trafficBadSignal) {
        return new TestTomTomTrafficRefineClient(request -> new TomTomTrafficRefineResult(true, multiplier, 0L, 0.91, trafficBadSignal, 7L, ""));
    }

    public static TestTomTomTrafficRefineClient stale(double multiplier, long sourceAgeMs) {
        return new TestTomTomTrafficRefineClient(request -> new TomTomTrafficRefineResult(true, multiplier, sourceAgeMs, 0.91, true, 7L, "tomtom-stale"));
    }

    public static TestTomTomTrafficRefineClient unavailable(String degradeReason) {
        return new TestTomTomTrafficRefineClient(request -> new TomTomTrafficRefineResult(false, 1.0, Long.MAX_VALUE, 0.0, false, 7L, degradeReason));
    }

    @Override
    public TomTomTrafficRefineResult refine(EtaEstimateRequest request, double baselineMinutes, double distanceKm) {
        return function.apply(request);
    }
}
