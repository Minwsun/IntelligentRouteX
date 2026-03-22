package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * ETA prediction model — learns from actual delivery times.
 *
 * Input: 6D (distanceKm, traffic, weather, hour, bundleSize, congestion)
 * Output: predicted ETA in minutes
 *
 * Self-trains: on every delivery completion, update(features, actualETAMinutes).
 * Fallback: if not warmed up, returns heuristic estimate.
 */
public class ETAModel {

    private final OnlineRegressor regressor;
    private static final double HEURISTIC_SPEED_KMH = 20.0;

    public ETAModel() {
        // 6 input features, lr=0.01
        this.regressor = new OnlineRegressor(6, 0.01, 0.0005, 101L);
    }

    /**
     * Predict ETA in minutes.
     */
    public double predict(double[] etaFeatures) {
        if (!regressor.isWarmedUp()) {
            return heuristicETA(etaFeatures);
        }
        double pred = regressor.predict(etaFeatures);
        return Math.max(1.0, pred); // minimum 1 minute
    }

    /**
     * Update with actual delivery time.
     */
    public void update(double[] etaFeatures, double actualETAMinutes) {
        regressor.update(etaFeatures, actualETAMinutes);
    }

    /**
     * Heuristic fallback: distance / speed × adjustments.
     */
    private double heuristicETA(double[] f) {
        double distKm = f[0] * 15.0; // denormalize
        double traffic = f[1];
        double weather = f[2] * 3.0;

        double speed = HEURISTIC_SPEED_KMH * (1.0 - traffic * 0.4);
        if (weather > 1.5) speed *= 0.7; // heavy rain
        if (weather > 2.5) speed *= 0.5; // storm
        speed = Math.max(6.0, speed);

        return (distKm / speed) * 60.0;
    }

    public boolean isWarmedUp() { return regressor.isWarmedUp(); }
    public double getRunningMSE() { return regressor.getRunningMSE(); }
    public long getSampleCount() { return regressor.getSampleCount(); }

    public void reset() { regressor.reset(101L); }
}
