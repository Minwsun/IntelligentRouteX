package com.routechain.v2.context;

public enum WeatherState {
    CLEAR(1.00, 0.05),
    LIGHT_RAIN(1.07, 0.20),
    MODERATE_RAIN(1.16, 0.45),
    HEAVY_RAIN(1.28, 0.75),
    STORM(1.38, 0.95);

    private final double etaMultiplier;
    private final double severity;

    WeatherState(double etaMultiplier, double severity) {
        this.etaMultiplier = etaMultiplier;
        this.severity = severity;
    }

    public double etaMultiplier() {
        return etaMultiplier;
    }

    public double severity() {
        return severity;
    }
}
