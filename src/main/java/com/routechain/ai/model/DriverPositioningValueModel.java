package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Learned value model for end-zone landing and idle-driver positioning.
 */
public class DriverPositioningValueModel {

    private final OnlineRegressor regressor;

    public DriverPositioningValueModel() {
        this.regressor = new OnlineRegressor(11, 0.005, 0.001, 1001L);
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
        regressor.reset(1001L);
    }

    public static double computeOutcomeLabel(double continuationActualNorm,
                                             double expectedEmptyKm,
                                             double realizedProfit,
                                             boolean cancelled) {
        double score = clamp01(continuationActualNorm) * 0.42
                + clamp01(1.0 - expectedEmptyKm / 4.0) * 0.28
                + clamp01(realizedProfit / 60000.0) * 0.20
                - (cancelled ? 0.18 : 0.0);
        return clamp01(score);
    }

    private double heuristic(double[] f) {
        if (f == null || f.length == 0) {
            return 0.0;
        }
        double demand5 = f[0];
        double demand10 = f[1];
        double demand15 = f[2];
        double postDrop = f[3];
        double emptyRisk = f[4];
        double graphCentrality = f[5];
        double shortage = f[6];
        double congestion = f[7];
        double weather = f[8];
        double distancePenalty = f[9];
        double attraction = f[10];

        double score = demand5 * 0.08
                + demand10 * 0.16
                + demand15 * 0.10
                + postDrop * 0.22
                + graphCentrality * 0.12
                + shortage * 0.08
                + attraction * 0.16
                - emptyRisk * 0.18
                - congestion * 0.06
                - weather * 0.04
                - distancePenalty * 0.06;
        return clamp01(score);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
