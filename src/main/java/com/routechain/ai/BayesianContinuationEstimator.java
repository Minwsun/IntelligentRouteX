package com.routechain.ai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bayesian online estimate for post-drop hit, idle and empty distance.
 */
public final class BayesianContinuationEstimator {
    private final Map<String, BucketStats> buckets = new ConcurrentHashMap<>();

    public BayesianOutcomeEstimate estimate(GraphRouteState state) {
        if (state == null) {
            return new BayesianOutcomeEstimate(0.35, 5.0, 1.4, 0.0);
        }
        BucketStats stats = buckets.get(bucketKey(state));
        if (stats == null) {
            return new BayesianOutcomeEstimate(
                    clamp01(state.postDropDemandProbability() * 0.86 + state.futureOpportunity() * 0.14),
                    Math.max(1.5, state.expectedNextOrderIdleMinutes()),
                    Math.max(0.4, state.expectedPostCompletionEmptyKm()),
                    0.18);
        }
        double hit = stats.alpha / (stats.alpha + stats.beta);
        double idle = stats.idleSum / Math.max(1, stats.sampleCount);
        double emptyKm = stats.emptyKmSum / Math.max(1, stats.sampleCount);
        double confidence = Math.min(0.95, 0.20 + stats.sampleCount / 80.0);
        return new BayesianOutcomeEstimate(
                clamp01(hit * 0.82 + state.postDropDemandProbability() * 0.18),
                Math.max(0.0, idle),
                Math.max(0.0, emptyKm),
                confidence);
    }

    public void update(GraphRouteState state,
                       boolean postDropHit,
                       double nextOrderIdleMinutes,
                       double expectedEmptyKm) {
        if (state == null) {
            return;
        }
        BucketStats stats = buckets.computeIfAbsent(bucketKey(state), ignored -> new BucketStats());
        if (postDropHit) {
            stats.alpha += 1.0;
        } else {
            stats.beta += 1.0;
        }
        stats.idleSum += Math.max(0.0, nextOrderIdleMinutes);
        stats.emptyKmSum += Math.max(0.0, expectedEmptyKm);
        stats.sampleCount++;
    }

    public void clear() {
        buckets.clear();
    }

    private String bucketKey(GraphRouteState state) {
        return state.zoneKey() + "|" + state.endHourBucket() / 3 + "|" + state.weatherProfile() + "|" + state.selectionBucket();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class BucketStats {
        private double alpha = 2.0;
        private double beta = 2.0;
        private double idleSum = 10.0;
        private double emptyKmSum = 2.8;
        private int sampleCount = 0;
    }
}
