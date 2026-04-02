package com.routechain.simulation;

/**
 * Delta between forecast calibration summaries.
 */
public record ForecastCalibrationSummaryDelta(
        double etaMaeMinutesDelta,
        double merchantPrepMaeMinutesDelta,
        double continuationCalibrationGapDelta,
        double avgPredictedPostDropOpportunityDelta
) {
    public static ForecastCalibrationSummaryDelta compare(ForecastCalibrationSummary baseline,
                                                          ForecastCalibrationSummary candidate) {
        ForecastCalibrationSummary safeBaseline = baseline == null
                ? ForecastCalibrationSummary.empty() : baseline;
        ForecastCalibrationSummary safeCandidate = candidate == null
                ? ForecastCalibrationSummary.empty() : candidate;
        return new ForecastCalibrationSummaryDelta(
                safeCandidate.etaMaeMinutes() - safeBaseline.etaMaeMinutes(),
                safeCandidate.merchantPrepMaeMinutes() - safeBaseline.merchantPrepMaeMinutes(),
                safeCandidate.continuationCalibrationGap() - safeBaseline.continuationCalibrationGap(),
                safeCandidate.avgPredictedPostDropOpportunity() - safeBaseline.avgPredictedPostDropOpportunity()
        );
    }
}
