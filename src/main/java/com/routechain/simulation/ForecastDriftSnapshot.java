package com.routechain.simulation;

/**
 * Forecast-quality snapshot used to gate model promotion and explain drift.
 */
public record ForecastDriftSnapshot(
        String runId,
        String scenarioName,
        double continuationCalibrationGap,
        double merchantPrepMaeMinutes,
        double trafficForecastError,
        double weatherForecastHitRate,
        double borrowSuccessCalibration,
        boolean drifted,
        String verdict,
        String note
) {
    public ForecastDriftSnapshot {
        runId = runId == null || runId.isBlank() ? "run-unset" : runId;
        scenarioName = scenarioName == null || scenarioName.isBlank() ? "unknown" : scenarioName;
        verdict = verdict == null || verdict.isBlank() ? "UNASSESSED" : verdict;
        note = note == null ? "" : note;
    }
}
