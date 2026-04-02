package com.routechain.simulation;

/**
 * Simple champion/challenger promotion recommendation for control-room ops.
 */
public record ModelPromotionDecision(
        String modelKey,
        String championVersion,
        String challengerVersion,
        String decision,
        String reason,
        boolean promoteNow,
        int latencyBudgetMs,
        double observedDispatchP95Ms,
        double businessScore,
        double calibrationGap
) {
    public ModelPromotionDecision {
        modelKey = modelKey == null || modelKey.isBlank() ? "unknown-model" : modelKey;
        championVersion = championVersion == null || championVersion.isBlank()
                ? modelKey + "-fallback-v1"
                : championVersion;
        challengerVersion = challengerVersion == null ? "" : challengerVersion;
        decision = decision == null || decision.isBlank() ? "KEEP_CHAMPION" : decision;
        reason = reason == null ? "" : reason;
        latencyBudgetMs = Math.max(10, latencyBudgetMs);
    }
}
