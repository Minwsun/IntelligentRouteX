package com.routechain.ai;

/**
 * Online posterior estimate for lateness and cancellation risk.
 */
public record BayesianRiskEstimate(
        double lateRiskProbability,
        double cancelRiskProbability,
        double confidence
) {}
