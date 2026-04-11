package com.routechain.ai;

/**
 * Delayed feedback entry for one chosen route action.
 */
public record DelayedRewardLedgerEntry(
        String traceId,
        long decisionTick,
        long eligibilityTick,
        long resolvedTick,
        GraphRouteState graphRouteState,
        double predictedReward,
        PseudoRewardEnvelope pseudoRewardEnvelope,
        OracleDisagreementSignal oracleDisagreementSignal,
        boolean chosen,
        boolean resolved,
        double realizedReward
) {
    public DelayedRewardLedgerEntry resolve(long completedTick, double reward) {
        return new DelayedRewardLedgerEntry(
                traceId,
                decisionTick,
                eligibilityTick,
                completedTick,
                graphRouteState,
                predictedReward,
                pseudoRewardEnvelope,
                oracleDisagreementSignal,
                chosen,
                true,
                reward);
    }

    public long delayedTicks() {
        if (!resolved) {
            return 0L;
        }
        return Math.max(0L, resolvedTick - decisionTick);
    }
}
