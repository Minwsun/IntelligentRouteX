package com.routechain.v2.context;

public final class EtaUncertaintyEstimator {
    public double estimate(TrafficProfileSnapshot traffic,
                           WeatherContextSnapshot weather,
                           boolean liveRefineApplied,
                           boolean mlResidualApplied,
                           FreshnessMetadata freshness) {
        double uncertainty = 0.08;
        uncertainty += (1.0 - safeConfidence(traffic == null ? 0.0 : traffic.confidence())) * 0.20;
        uncertainty += (1.0 - safeConfidence(weather == null ? 0.0 : weather.confidence())) * 0.20;
        if (freshness != null && !freshness.trafficFresh()) {
            uncertainty += 0.12;
        }
        if (freshness != null && !freshness.weatherFresh()) {
            uncertainty += 0.12;
        }
        if (!liveRefineApplied) {
            uncertainty += 0.05;
        }
        if (!mlResidualApplied) {
            uncertainty += 0.05;
        }
        return Math.max(0.05, Math.min(1.0, uncertainty));
    }

    private double safeConfidence(double confidence) {
        return Math.max(0.0, Math.min(1.0, confidence));
    }
}
