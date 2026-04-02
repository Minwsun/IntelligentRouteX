package com.routechain.simulation;

/**
 * Delta between two intelligence scorecards.
 */
public record IntelligenceScorecardDelta(
        double businessScoreDelta,
        double routingScoreDelta,
        double networkScoreDelta,
        double forecastScoreDelta,
        double weatherAvoidanceQualityDelta,
        double congestionAvoidanceQualityDelta,
        double waveQualityDelta,
        double noDriverFoundRateDelta,
        double reserveStabilityDelta,
        double coverageRecoveryRateDelta,
        double emptyZoneRecoveryRateDelta,
        double trafficForecastErrorDelta,
        double weatherForecastHitRateDelta,
        double borrowSuccessCalibrationDelta
) {
    public static IntelligenceScorecardDelta compare(IntelligenceScorecard baseline,
                                                     IntelligenceScorecard candidate) {
        IntelligenceScorecard left = baseline == null ? IntelligenceScorecard.empty() : baseline;
        IntelligenceScorecard right = candidate == null ? IntelligenceScorecard.empty() : candidate;
        return new IntelligenceScorecardDelta(
                right.businessScore() - left.businessScore(),
                right.routingScore() - left.routingScore(),
                right.networkScore() - left.networkScore(),
                right.forecastScore() - left.forecastScore(),
                right.weatherAvoidanceQuality() - left.weatherAvoidanceQuality(),
                right.congestionAvoidanceQuality() - left.congestionAvoidanceQuality(),
                right.waveQuality() - left.waveQuality(),
                right.noDriverFoundRate() - left.noDriverFoundRate(),
                right.reserveStability() - left.reserveStability(),
                right.coverageRecoveryRate() - left.coverageRecoveryRate(),
                right.emptyZoneRecoveryRate() - left.emptyZoneRecoveryRate(),
                right.trafficForecastError() - left.trafficForecastError(),
                right.weatherForecastHitRate() - left.weatherForecastHitRate(),
                right.borrowSuccessCalibration() - left.borrowSuccessCalibration()
        );
    }
}
