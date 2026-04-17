package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;

public final class WeatherConfidenceEvaluator {
    private final RouteChainDispatchV2Properties properties;

    public WeatherConfidenceEvaluator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public double effectiveConfidence(double rawConfidence, boolean fresh) {
        double bounded = Math.max(0.0, Math.min(1.0, rawConfidence));
        if (fresh) {
            return bounded;
        }
        return Math.min(bounded, Math.max(0.15, properties.getWeather().getConfidenceThreshold() - 0.10));
    }

    public boolean passesThreshold(double confidence) {
        return confidence >= properties.getWeather().getConfidenceThreshold();
    }
}
