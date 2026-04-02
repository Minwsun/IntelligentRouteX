package com.routechain.simulation;

/**
 * Forecast quality diagnostics for one run.
 */
public record ForecastCalibrationSummary(
        double etaMaeMinutes,
        double merchantPrepMaeMinutes,
        double continuationCalibrationGap,
        double avgPredictedPostDropOpportunity
) {
    public static ForecastCalibrationSummary empty() {
        return new ForecastCalibrationSummary(0.0, 0.0, 0.0, 0.0);
    }
}
