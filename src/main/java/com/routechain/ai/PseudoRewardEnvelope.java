package com.routechain.ai;

/**
 * Soft label for a route decision built from critics instead of retraining.
 */
public record PseudoRewardEnvelope(
        double oracleReward,
        double retrievalReward,
        double futureReward,
        double riskPenalty,
        double disagreementPenalty
) {
    public double estimatedReward() {
        return clamp01(
                oracleReward * 0.36
                        + retrievalReward * 0.24
                        + futureReward * 0.28
                        - riskPenalty * 0.08
                        - disagreementPenalty * 0.04);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
