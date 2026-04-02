package com.routechain.simulation;

/**
 * Balanced intelligence assessment of one run.
 */
public record IntelligenceScorecard(
        double businessScore,
        double routingScore,
        double networkScore,
        double forecastScore,
        double weatherAvoidanceQuality,
        double congestionAvoidanceQuality,
        double waveQuality,
        double noDriverFoundRate,
        double reserveStability,
        double coverageRecoveryRate,
        double emptyZoneRecoveryRate,
        double trafficForecastError,
        double weatherForecastHitRate,
        double borrowSuccessCalibration,
        String primaryVerdict,
        String secondaryVerdict
) {
    public static IntelligenceScorecard empty() {
        return new IntelligenceScorecard(
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0,
                "UNASSESSED", "UNASSESSED");
    }
}
