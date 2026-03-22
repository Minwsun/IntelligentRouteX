package com.routechain.ai;

/**
 * Pure-Java Online SGD Linear Regressor.
 * Base model for all prediction components in RouteChain Omega.
 *
 * Uses Stochastic Gradient Descent with:
 * - L2 regularization (weight decay)
 * - Learning rate decay (1/sqrt(t))
 * - Gradient clipping for stability
 */
public class OnlineRegressor {

    private final double[] weights;
    private double bias;
    private final double baseLearningRate;
    private final double l2Lambda;
    private long sampleCount;
    private double runningMSE;

    /**
     * @param featureDim   number of input features
     * @param learningRate base SGD step size (decays over time)
     * @param l2Lambda     L2 regularization strength
     * @param seed         random seed for weight initialization
     */
    public OnlineRegressor(int featureDim, double learningRate, double l2Lambda, long seed) {
        this.weights = new double[featureDim];
        this.baseLearningRate = learningRate;
        this.l2Lambda = l2Lambda;
        this.sampleCount = 0;
        this.runningMSE = 0;
        this.bias = 0;

        // Xavier-like initialization with seed
        java.util.Random rng = new java.util.Random(seed);
        double scale = Math.sqrt(2.0 / featureDim);
        for (int i = 0; i < featureDim; i++) {
            weights[i] = rng.nextGaussian() * scale;
        }
        bias = rng.nextGaussian() * 0.01;
    }

    /**
     * Convenience constructor with default L2=0.001 and seed=42.
     */
    public OnlineRegressor(int featureDim, double learningRate) {
        this(featureDim, learningRate, 0.001, 42L);
    }

    // ── Prediction ──────────────────────────────────────────────────────

    /**
     * Forward pass: dot(weights, features) + bias.
     */
    public double predict(double[] features) {
        if (features.length != weights.length) {
            throw new IllegalArgumentException(
                    "Feature dim mismatch: expected " + weights.length + ", got " + features.length);
        }
        double sum = bias;
        for (int i = 0; i < weights.length; i++) {
            sum += weights[i] * features[i];
        }
        return sum;
    }

    /**
     * Predict with sigmoid activation → output in [0, 1].
     * Use for probability models (late risk, cancel risk).
     */
    public double predictProbability(double[] features) {
        return sigmoid(predict(features));
    }

    // ── Online learning ─────────────────────────────────────────────────

    /**
     * Single-sample SGD update.
     * w_i = w_i - lr * (predicted - actual) * x_i - lr * lambda * w_i
     */
    public void update(double[] features, double actualTarget) {
        if (features.length != weights.length) {
            throw new IllegalArgumentException(
                    "Feature dim mismatch: expected " + weights.length + ", got " + features.length);
        }

        sampleCount++;
        double lr = effectiveLearningRate();
        double predicted = predict(features);
        double error = predicted - actualTarget;

        // Update running MSE (exponential moving average)
        runningMSE = 0.99 * runningMSE + 0.01 * error * error;

        // Gradient clipping
        double clippedError = Math.max(-5.0, Math.min(5.0, error));

        // SGD with L2 regularization
        for (int i = 0; i < weights.length; i++) {
            double gradient = clippedError * features[i] + l2Lambda * weights[i];
            weights[i] -= lr * gradient;
        }
        bias -= lr * clippedError;
    }

    /**
     * Update for probability targets (uses cross-entropy gradient).
     */
    public void updateProbability(double[] features, double actualTarget) {
        sampleCount++;
        double lr = effectiveLearningRate();
        double predicted = predictProbability(features);
        double error = predicted - actualTarget; // cross-entropy gradient simplified

        double clippedError = Math.max(-5.0, Math.min(5.0, error));

        for (int i = 0; i < weights.length; i++) {
            double gradient = clippedError * features[i] + l2Lambda * weights[i];
            weights[i] -= lr * gradient;
        }
        bias -= lr * clippedError;
    }

    // ── Batch training ──────────────────────────────────────────────────

    /**
     * Mini-batch SGD over dataset for multiple epochs.
     */
    public void batchTrain(double[][] features, double[] targets, int epochs) {
        for (int epoch = 0; epoch < epochs; epoch++) {
            for (int s = 0; s < features.length; s++) {
                update(features[s], targets[s]);
            }
        }
    }

    // ── Accessors ───────────────────────────────────────────────────────

    public double[] getWeights() { return weights.clone(); }
    public double getBias() { return bias; }
    public long getSampleCount() { return sampleCount; }
    public double getRunningMSE() { return runningMSE; }
    public int getFeatureDim() { return weights.length; }

    /**
     * Model readiness: has seen enough samples to be trusted.
     */
    public boolean isWarmedUp() {
        return sampleCount >= 50;
    }

    /**
     * Reset weights to initial state.
     */
    public void reset(long seed) {
        java.util.Random rng = new java.util.Random(seed);
        double scale = Math.sqrt(2.0 / weights.length);
        for (int i = 0; i < weights.length; i++) {
            weights[i] = rng.nextGaussian() * scale;
        }
        bias = rng.nextGaussian() * 0.01;
        sampleCount = 0;
        runningMSE = 0;
    }

    // ── Internal ────────────────────────────────────────────────────────

    private double effectiveLearningRate() {
        // Decay: lr / sqrt(1 + t/100)
        return baseLearningRate / Math.sqrt(1.0 + sampleCount / 100.0);
    }

    private static double sigmoid(double x) {
        if (x > 10) return 1.0;
        if (x < -10) return 0.0;
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
