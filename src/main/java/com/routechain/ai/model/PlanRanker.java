package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Plan Ranker — learned utility model replacing hard-coded PlanScoreCalculator weights.
 *
 * Input: 15D plan features
 * Output: predicted utility score
 *
 * Training signal: actual utility computed from delivery outcomes.
 *
 * ActualUtility = + onTimeBonus * 0.3        (1.0 if on-time, 0.0 if late)
 *                 + profitNorm * 0.25         (profit / 50000)
 *                 + bundleEfficiency * 0.15   (distance saved)
 *                 + continuationActual * 0.15 (actual future earning normalized)
 *                 - deadheadPenalty * 0.10     (deadhead / 6km)
 *                 - cancelPenalty * 0.05       (1.0 if cancelled, 0 if not)
 *
 * The model LEARNS these weights from data instead of using fixed values.
 */
public class PlanRanker {

    private final OnlineRegressor regressor;

    public PlanRanker() {
        this.regressor = new OnlineRegressor(15, 0.008, 0.001, 606L);
    }

    /**
     * Rank a plan by predicted utility.
     */
    public double rank(double[] planFeatures) {
        if (!regressor.isWarmedUp()) {
            return heuristicRank(planFeatures);
        }
        return regressor.predict(planFeatures);
    }

    /**
     * Train with actual utility label.
     */
    public void learnFromOutcome(double[] planFeatures, double actualUtility) {
        regressor.update(planFeatures, actualUtility);
    }

    /**
     * Batch retrain from decision log historical data.
     */
    public void batchRetrain(double[][] features, double[] utilities, int epochs) {
        regressor.batchTrain(features, utilities, epochs);
    }

    /**
     * Compute actual utility label from delivery outcomes.
     * Call this when a plan's orders are all completed to create training signal.
     */
    public static double computeActualUtility(
            boolean wasOnTime, double profitVND, double bundleEfficiency,
            double continuationActualNorm, double deadheadKm, boolean wasCancelled) {
        return (wasOnTime ? 1.0 : 0.0) * 0.30
                + Math.max(-1.0, Math.min(1.0, profitVND / 50000.0)) * 0.25
                + bundleEfficiency * 0.15
                + continuationActualNorm * 0.15
                - Math.min(1.0, deadheadKm / 6.0) * 0.10
                - (wasCancelled ? 1.0 : 0.0) * 0.05;
    }

    /**
     * Heuristic rank when model is cold.
     * Uses fixed weights similar to old PlanScoreCalculator.
     */
    private double heuristicRank(double[] f) {
        // f[0]=bundleSize, f[3]=ETA, f[4]=lateRisk, f[5]=cancelRisk,
        // f[6]=profitNorm, f[8]=deadhead, f[9]=continuationValue, f[12]=congestion
        return 0.18 * (1.0 - f[4])    // on-time = 1 - lateRisk
             + 0.15 * f[6]            // profit
             + 0.12 * f[0]            // bundle efficiency proxy
             + 0.12 * f[9]            // continuation value
             + 0.08 * (1.0 - f[7])    // fee fairness (lower fee = better)
             + 0.10 * f[9]            // next order proxy
             - 0.08 * f[8]            // deadhead
             - 0.05 * f[12]           // congestion
             - 0.05 * f[4]            // late risk penalty
             - 0.03 * f[5];           // cancel risk penalty
    }

    public boolean isWarmedUp() { return regressor.isWarmedUp(); }
    public double getRunningMSE() { return regressor.getRunningMSE(); }
    public long getSampleCount() { return regressor.getSampleCount(); }
    public double[] getLearnedWeights() { return regressor.getWeights(); }

    public void reset() { regressor.reset(606L); }
}
