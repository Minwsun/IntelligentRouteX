package com.routechain.v2.integration;

public interface ForecastClient {
    ForecastResult forecastDemandShift(DemandShiftFeatureVector featureVector, long timeoutBudgetMs);

    ForecastResult forecastZoneBurst(ZoneBurstFeatureVector featureVector, long timeoutBudgetMs);

    ForecastResult forecastPostDropShift(PostDropShiftFeatureVector featureVector, long timeoutBudgetMs);

    WorkerReadyState readyState();
}
