package com.routechain.ai;

/**
 * Online posterior estimate for future route outcome quality.
 */
public record BayesianOutcomeEstimate(
        double postDropHitProbability,
        double expectedIdleMinutes,
        double expectedEmptyKm,
        double confidence
) {}
