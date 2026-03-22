package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Cancellation Risk model — learns from actual cancel/complete outcomes.
 *
 * Input: 5D (waitTime, lateRisk, fee, weather, bundleSize)
 * Output: P(cancel) in [0, 1]
 */
public class CancelRiskModel {

    private final OnlineRegressor regressor;

    public CancelRiskModel() {
        this.regressor = new OnlineRegressor(5, 0.02, 0.0005, 303L);
    }

    public double predict(double[] features) {
        if (!regressor.isWarmedUp()) {
            return heuristicCancelRisk(features);
        }
        return regressor.predictProbability(features);
    }

    public void update(double[] features, double wasCancelled) {
        regressor.updateProbability(features, wasCancelled);
    }

    private double heuristicCancelRisk(double[] f) {
        double wait = f[0]; // normalized waitTime
        double lateRisk = f[1];
        double weather = f[3] * 3.0;

        double risk = wait * 0.4 + lateRisk * 0.3;
        if (weather > 2.0) risk += 0.1;
        return Math.max(0, Math.min(0.5, risk)); // cap at 50%
    }

    public boolean isWarmedUp() { return regressor.isWarmedUp(); }
    public void reset() { regressor.reset(303L); }
}
