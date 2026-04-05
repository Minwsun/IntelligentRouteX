package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Learned gate for deciding whether a fallback/rescue assignment is truly worth
 * taking under stress instead of blindly forcing nearest-driver coverage.
 */
public class StressRescueModel {

    private final OnlineRegressor regressor;

    public StressRescueModel() {
        this.regressor = new OnlineRegressor(10, 0.006, 0.001, 909L);
    }

    public double predict(double[] features) {
        if (!regressor.isWarmedUp()) {
            return heuristic(features);
        }
        return clamp01(regressor.predictProbability(features));
    }

    public void learnFromOutcome(double[] features, double label) {
        regressor.updateProbability(features, clamp01(label));
    }

    public boolean isWarmedUp() {
        return regressor.isWarmedUp();
    }

    public long getSampleCount() {
        return regressor.getSampleCount();
    }

    public void reset() {
        regressor.reset(909L);
    }

    public static double computeOutcomeLabel(boolean onTime,
                                             boolean cancelled,
                                             double deadheadKm,
                                             double continuationActualNorm,
                                             double realizedProfit) {
        double score = (onTime ? 0.30 : 0.0)
                + (cancelled ? 0.0 : 0.18)
                + clamp01(1.0 - deadheadKm / 4.0) * 0.22
                + clamp01(continuationActualNorm) * 0.16
                + clamp01(realizedProfit / 50000.0) * 0.14;
        return clamp01(score);
    }

    private double heuristic(double[] f) {
        if (f == null || f.length == 0) {
            return 0.0;
        }
        double stressIntensity = f[0];
        double onTime = f[1];
        double deadhead = f[2];
        double pickupReady = f[3];
        double sameZone = f[4];
        double localBacklogTight = f[5];
        double borrowRisk = f[6];
        double merchantPrepRisk = f[7];
        double weather = f[8];
        double traffic = f[9];

        double score = onTime * 0.30
                + pickupReady * 0.10
                + sameZone * 0.18
                + localBacklogTight * 0.08
                + clamp01(1.0 - deadhead) * 0.20
                + clamp01(1.0 - borrowRisk) * 0.08
                + clamp01(1.0 - merchantPrepRisk) * 0.06
                + clamp01(1.0 - weather) * 0.04
                + clamp01(1.0 - traffic) * 0.04
                - stressIntensity * 0.10;
        return clamp01(score);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
