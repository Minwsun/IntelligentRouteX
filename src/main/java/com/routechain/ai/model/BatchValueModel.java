package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Learned value model for deciding whether a bundle is actually better than
 * serving the same demand as smaller local alternatives.
 */
public class BatchValueModel {

    private final OnlineRegressor regressor;

    public BatchValueModel() {
        this.regressor = new OnlineRegressor(12, 0.006, 0.001, 808L);
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

    public long getSampleCount() {
        return regressor.getSampleCount();
    }

    public boolean isWarmedUp() {
        return regressor.isWarmedUp();
    }

    public void reset() {
        regressor.reset(808L);
    }

    public static double computeOutcomeLabel(boolean onTime,
                                             double bundleEfficiency,
                                             double continuationActualNorm,
                                             double deadheadKm,
                                             double realizedProfit,
                                             boolean cancelled) {
        double score = (onTime ? 0.28 : 0.0)
                + clamp01(bundleEfficiency) * 0.28
                + clamp01(continuationActualNorm) * 0.18
                + clamp01(realizedProfit / 60000.0) * 0.16
                + clamp01(1.0 - deadheadKm / 4.0) * 0.16
                - (cancelled ? 0.20 : 0.0);
        return clamp01(score);
    }

    private double heuristic(double[] f) {
        if (f == null || f.length == 0) {
            return 0.0;
        }
        double bundleSize = f[0];
        double compactness = f[1];
        double dropCoherence = f[2];
        double bundleEfficiency = f[3];
        double onTime = f[4];
        double lateRisk = f[5];
        double deadhead = f[6];
        double postDrop = f[7];
        double landing = f[8];
        double emptyRisk = f[9];
        double borrowed = f[10];
        double zigZag = f[11];

        double score = bundleSize * 0.08
                + compactness * 0.15
                + dropCoherence * 0.14
                + bundleEfficiency * 0.18
                + onTime * 0.12
                + postDrop * 0.10
                + landing * 0.10
                - lateRisk * 0.05
                - deadhead * 0.12
                - emptyRisk * 0.08
                - borrowed * 0.05
                - zigZag * 0.07;
        return clamp01(score);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
