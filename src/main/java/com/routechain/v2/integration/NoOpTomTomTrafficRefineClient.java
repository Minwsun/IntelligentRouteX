package com.routechain.v2.integration;

import com.routechain.v2.context.EtaEstimateRequest;

public final class NoOpTomTomTrafficRefineClient implements TomTomTrafficRefineClient {
    @Override
    public TomTomTrafficRefineResult refine(EtaEstimateRequest request, double baselineMinutes, double distanceKm) {
        return TomTomTrafficRefineResult.notApplied();
    }
}
