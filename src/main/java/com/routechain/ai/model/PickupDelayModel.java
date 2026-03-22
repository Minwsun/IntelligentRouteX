package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Pickup Delay model — predicts how long driver waits at pickup point.
 *
 * Input: 4D (traffic, weather, distanceKm, hour)
 * Output: predicted wait time in minutes
 */
public class PickupDelayModel {

    private final OnlineRegressor regressor;

    public PickupDelayModel() {
        this.regressor = new OnlineRegressor(4, 0.01, 0.001, 404L);
    }

    public double predict(double[] features) {
        if (!regressor.isWarmedUp()) {
            return heuristicDelay(features);
        }
        return Math.max(0.5, regressor.predict(features));
    }

    public void update(double[] features, double actualDelayMinutes) {
        regressor.update(features, actualDelayMinutes);
    }

    private double heuristicDelay(double[] f) {
        double traffic = f[0];
        double weather = f[1] * 3.0;
        // Base 2 min delay + traffic/weather adjustments
        double delay = 2.0 + traffic * 3.0;
        if (weather > 1.5) delay += 2.0;
        if (weather > 2.5) delay += 3.0;
        return delay;
    }

    public boolean isWarmedUp() { return regressor.isWarmedUp(); }
    public void reset() { regressor.reset(404L); }
}
