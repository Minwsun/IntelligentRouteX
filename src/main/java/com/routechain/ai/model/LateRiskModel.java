package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Late Risk prediction model — learns from actual on-time/late outcomes.
 *
 * Input: 7D (distanceKm, traffic, weather, hour, bundleSize, slaSlack, pickupDelay)
 * Output: P(late) in [0, 1]
 *
 * Self-trains: on delivery, update(features, 1.0 if late, 0.0 if on-time).
 */
public class LateRiskModel {

    private final OnlineRegressor regressor;

    public LateRiskModel() {
        this.regressor = new OnlineRegressor(7, 0.02, 0.0005, 202L);
    }

    /**
     * Predict probability of late delivery.
     */
    public double predict(double[] riskFeatures) {
        if (!regressor.isWarmedUp()) {
            return heuristicLateRisk(riskFeatures);
        }
        return regressor.predictProbability(riskFeatures);
    }

    /**
     * Update with actual outcome.
     * @param wasLate 1.0 if delivered late, 0.0 if on-time
     */
    public void update(double[] riskFeatures, double wasLate) {
        regressor.updateProbability(riskFeatures, wasLate);
    }

    /**
     * Heuristic: late risk increases with traffic, rain, small SLA slack.
     */
    private double heuristicLateRisk(double[] f) {
        double traffic = f[1];
        double weather = f[2] * 3.0;
        double slaSlack = f[5]; // normalized, can be negative

        double risk = 0;
        risk += traffic * 0.3;
        if (weather > 1.5) risk += 0.15;
        if (weather > 2.5) risk += 0.2;
        if (slaSlack < 0) risk += 0.25; // already past SLA slack
        if (slaSlack < -0.3) risk += 0.2;
        risk += f[4] * 0.1; // bundleSize adds risk

        return Math.max(0, Math.min(1.0, risk));
    }

    public boolean isWarmedUp() { return regressor.isWarmedUp(); }
    public double getRunningMSE() { return regressor.getRunningMSE(); }
    public void reset() { regressor.reset(202L); }
}
