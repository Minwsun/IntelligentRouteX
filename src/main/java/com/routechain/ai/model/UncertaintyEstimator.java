package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Uncertainty Estimator — ensemble of 3 regressors with different seeds.
 * Outputs mean prediction + variance for robust decision-making.
 *
 * RobustScore = mean - λ * sqrt(variance)
 *
 * This is what makes the system prefer STABLE plans over HIGH-RISK, HIGH-REWARD ones.
 * A plan with mean=0.8 and variance=0.1 beats a plan with mean=0.85 and variance=0.4.
 */
public class UncertaintyEstimator {

    private static final int ENSEMBLE_SIZE = 3;
    private static final double RISK_AVERSION_LAMBDA = 0.5;

    private final OnlineRegressor[] ensemble;

    /**
     * @param featureDim       input feature dimension
     * @param learningRate     SGD step size
     */
    public UncertaintyEstimator(int featureDim, double learningRate) {
        this.ensemble = new OnlineRegressor[ENSEMBLE_SIZE];
        for (int i = 0; i < ENSEMBLE_SIZE; i++) {
            // Each model uses a different seed → different weight initialization
            ensemble[i] = new OnlineRegressor(featureDim, learningRate, 0.001, 700L + i * 137);
        }
    }

    /**
     * Default constructor for plan evaluation (15D features).
     */
    public UncertaintyEstimator() {
        this(15, 0.008);
    }

    // ── Prediction ──────────────────────────────────────────────────────

    /**
     * Predict with uncertainty estimation.
     */
    public Prediction predict(double[] features) {
        double[] predictions = new double[ENSEMBLE_SIZE];
        for (int i = 0; i < ENSEMBLE_SIZE; i++) {
            predictions[i] = ensemble[i].predict(features);
        }

        double mean = 0;
        for (double p : predictions) mean += p;
        mean /= ENSEMBLE_SIZE;

        double variance = 0;
        for (double p : predictions) variance += (p - mean) * (p - mean);
        variance /= ENSEMBLE_SIZE;

        double confidence = 1.0 / (1.0 + Math.sqrt(variance));

        return new Prediction(mean, variance, confidence);
    }

    /**
     * Compute Robust Utility: mean - λ * sqrt(variance).
     * Lower variance → less penalty → preferred.
     */
    public double robustScore(double[] features) {
        Prediction pred = predict(features);
        return pred.mean - RISK_AVERSION_LAMBDA * Math.sqrt(pred.variance);
    }

    /**
     * Compute Robust Utility with custom lambda.
     */
    public double robustScore(double[] features, double lambda) {
        Prediction pred = predict(features);
        return pred.mean - lambda * Math.sqrt(pred.variance);
    }

    // ── Training ────────────────────────────────────────────────────────

    /**
     * Update all ensemble members with same training signal.
     */
    public void update(double[] features, double target) {
        for (OnlineRegressor model : ensemble) {
            model.update(features, target);
        }
    }

    /**
     * Batch train all ensemble members.
     */
    public void batchTrain(double[][] features, double[] targets, int epochs) {
        for (OnlineRegressor model : ensemble) {
            model.batchTrain(features, targets, epochs);
        }
    }

    // ── Status ──────────────────────────────────────────────────────────

    public boolean isWarmedUp() {
        return ensemble[0].isWarmedUp(); // all see same data, so check first
    }

    public long getSampleCount() { return ensemble[0].getSampleCount(); }

    public void reset() {
        for (int i = 0; i < ENSEMBLE_SIZE; i++) {
            ensemble[i].reset(700L + i * 137);
        }
    }

    // ── Output ──────────────────────────────────────────────────────────

    /**
     * Prediction result with uncertainty.
     */
    public record Prediction(double mean, double variance, double confidence) {
        /** Standard deviation. */
        public double std() { return Math.sqrt(variance); }

        /** Is this prediction high-confidence? (variance < threshold) */
        public boolean isConfident() { return confidence > 0.6; }
    }
}
