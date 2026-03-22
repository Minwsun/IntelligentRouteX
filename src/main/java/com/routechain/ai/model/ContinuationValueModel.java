package com.routechain.ai.model;

import com.routechain.ai.OnlineRegressor;

/**
 * Continuation Value Model — the "crown jewel" of RouteChain Omega.
 * Predicts future earning potential 10-15 minutes after plan completion.
 *
 * Input: 10D end-state features (zone demand, spike, driver density, shortage,
 *        exit traffic, hour, weather, driver utilization, completed orders)
 * Output: expected future earning in VND (next 10-15 minutes)
 *
 * Self-training protocol:
 * 1. When plan completion happens, record end-state features
 * 2. Wait 10 minutes (simulation ticks)
 * 3. Measure actual earning in that window
 * 4. update(endStateFeatures, actualEarning)
 *
 * This is what makes the system truly "look ahead" — it doesn't just optimize
 * the current delivery, it optimizes WHERE the driver ends up.
 */
public class ContinuationValueModel {

    private static final int CONTINUATION_WINDOW_TICKS = 10; // 10 minutes
    private static final double AVG_ORDER_VALUE = 25000.0; // VND

    private final OnlineRegressor regressor;

    // Pending outcomes: snapshots waiting for the window to elapse
    private final java.util.Map<String, PendingContinuation> pendingOutcomes
            = new java.util.concurrent.ConcurrentHashMap<>();

    public ContinuationValueModel() {
        this.regressor = new OnlineRegressor(10, 0.005, 0.001, 505L);
    }

    /**
     * Predict future earning from an end-state position.
     */
    public double predict(double[] endStateFeatures) {
        if (!regressor.isWarmedUp()) {
            return heuristicContinuationValue(endStateFeatures);
        }
        double pred = regressor.predict(endStateFeatures);
        return Math.max(0, pred); // future earning can't be negative
    }

    /**
     * Normalized continuation value in [0, 1] for scoring.
     */
    public double predictNormalized(double[] endStateFeatures) {
        return Math.min(1.0, predict(endStateFeatures) / (AVG_ORDER_VALUE * 2));
    }

    /**
     * Record that a driver completed a plan at given end-state.
     * The system will wait CONTINUATION_WINDOW_TICKS before measuring outcome.
     */
    public void recordCompletion(String driverId, long completionTick,
                                  double[] endStateFeatures, double earningAtCompletion) {
        pendingOutcomes.put(driverId + "-" + completionTick,
                new PendingContinuation(endStateFeatures, completionTick, earningAtCompletion));
    }

    /**
     * Check pending outcomes and train on any that have elapsed their window.
     * Call once per tick.
     */
    public void tickAndTrain(long currentTick,
                              java.util.function.Function<String, Double> driverEarningLookup) {
        var iterator = pendingOutcomes.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingContinuation pc = entry.getValue();

            if (currentTick - pc.completionTick >= CONTINUATION_WINDOW_TICKS) {
                String driverId = entry.getKey().split("-")[0];
                Double currentEarning = driverEarningLookup.apply(driverId);

                if (currentEarning != null) {
                    double actualFutureEarning = currentEarning - pc.earningAtCompletion;
                    regressor.update(pc.endStateFeatures, actualFutureEarning);
                }
                iterator.remove();
            }
        }
    }

    /**
     * Heuristic when model is cold: zone demand × avg value.
     */
    private double heuristicContinuationValue(double[] f) {
        double demand = f[0] * 3.0;   // denormalize
        double spike = f[2];
        double shortage = f[4];
        double hourNorm = f[6];

        // Higher demand, higher spike, higher shortage → more future earning
        double value = demand * AVG_ORDER_VALUE * 0.3
                + spike * AVG_ORDER_VALUE * 0.4
                + shortage * AVG_ORDER_VALUE * 0.2;

        // Peak hours boost
        double hour = hourNorm * 24.0;
        if ((hour >= 11 && hour <= 13) || (hour >= 17 && hour <= 19)) {
            value *= 1.3;
        }

        return Math.max(0, value);
    }

    public boolean isWarmedUp() { return regressor.isWarmedUp(); }
    public double getRunningMSE() { return regressor.getRunningMSE(); }
    public long getSampleCount() { return regressor.getSampleCount(); }
    public int getPendingCount() { return pendingOutcomes.size(); }

    public void reset() {
        regressor.reset(505L);
        pendingOutcomes.clear();
    }

    private record PendingContinuation(
            double[] endStateFeatures,
            long completionTick,
            double earningAtCompletion) {}
}
