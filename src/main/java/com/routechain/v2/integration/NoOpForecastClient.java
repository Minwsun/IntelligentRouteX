package com.routechain.v2.integration;

public final class NoOpForecastClient implements ForecastClient {
    @Override
    public ForecastResult forecastDemandShift(DemandShiftFeatureVector featureVector, long timeoutBudgetMs) {
        return ForecastResult.notApplied("forecast-client-disabled");
    }

    @Override
    public ForecastResult forecastZoneBurst(ZoneBurstFeatureVector featureVector, long timeoutBudgetMs) {
        return ForecastResult.notApplied("forecast-client-disabled");
    }

    @Override
    public ForecastResult forecastPostDropShift(PostDropShiftFeatureVector featureVector, long timeoutBudgetMs) {
        return ForecastResult.notApplied("forecast-client-disabled");
    }

    @Override
    public WorkerReadyState readyState() {
        return WorkerReadyState.notReady("forecast-client-disabled", MlWorkerMetadata.empty());
    }
}
