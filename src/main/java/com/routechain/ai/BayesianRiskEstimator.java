package com.routechain.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bayesian online risk envelopes for lateness and cancellation.
 */
public final class BayesianRiskEstimator {
    private final Map<String, RiskBucketStats> buckets = new ConcurrentHashMap<>();

    public BayesianRiskEstimate estimate(GraphRouteState state) {
        if (state == null) {
            return new BayesianRiskEstimate(0.22, 0.12, 0.0);
        }
        RiskBucketStats stats = buckets.get(bucketKey(state));
        if (stats == null) {
            return new BayesianRiskEstimate(
                    clamp01(state.slaRisk() * 0.82),
                    clamp01(state.stressPenalty() * 0.22 + state.pickupCost() * 0.10),
                    0.15);
        }
        double late = stats.lateAlpha / (stats.lateAlpha + stats.lateBeta);
        double cancel = stats.cancelAlpha / (stats.cancelAlpha + stats.cancelBeta);
        double confidence = Math.min(0.95, 0.18 + stats.sampleCount / 100.0);
        return new BayesianRiskEstimate(
                clamp01(late * 0.78 + state.slaRisk() * 0.22),
                clamp01(cancel * 0.70 + (state.stressPenalty() * 0.20 + state.pickupCost() * 0.10)),
                confidence);
    }

    public void update(GraphRouteState state, boolean wasLate, boolean wasCancelled) {
        if (state == null) {
            return;
        }
        RiskBucketStats stats = buckets.computeIfAbsent(bucketKey(state), ignored -> new RiskBucketStats());
        if (wasLate) {
            stats.lateAlpha += 1.0;
        } else {
            stats.lateBeta += 1.0;
        }
        if (wasCancelled) {
            stats.cancelAlpha += 1.0;
        } else {
        stats.cancelBeta += 1.0;
        }
        stats.sampleCount++;
    }

    public void clear() {
        buckets.clear();
    }

    private String bucketKey(GraphRouteState state) {
        return state.zoneKey() + "|" + state.weatherProfile() + "|" + state.bundleSize() + "|" + state.selectionBucket();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class RiskBucketStats {
        private double lateAlpha = 1.0;
        private double lateBeta = 4.0;
        private double cancelAlpha = 1.0;
        private double cancelBeta = 6.0;
        private int sampleCount = 0;
    }
}
