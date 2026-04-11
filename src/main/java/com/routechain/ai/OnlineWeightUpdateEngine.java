package com.routechain.ai;

/**
 * Online adaptive utility engine with bounded updates.
 */
public final class OnlineWeightUpdateEngine {
    private final DelayedLinearBanditEngine delegate = new DelayedLinearBanditEngine();

    public double score(GraphRouteState state,
                        BayesianOutcomeEstimate futureEstimate,
                        BayesianRiskEstimate riskEstimate,
                        RetrievedRouteAnalogs analogs,
                        ShadowOracleScore oracleScore) {
        PseudoRewardEnvelope pseudoRewardEnvelope = new PseudoRewardEnvelope(
                oracleScore == null ? 0.0 : oracleScore.oracleScore(),
                analogs == null ? 0.0 : analogs.analogScore(),
                futureEstimate == null ? 0.0 : futureEstimate.postDropHitProbability(),
                riskEstimate == null ? 0.0
                        : riskEstimate.lateRiskProbability() * 0.70 + riskEstimate.cancelRiskProbability() * 0.30,
                oracleScore == null ? 0.0 : oracleScore.disagreementPenalty());
        return delegate.score(
                state,
                futureEstimate,
                riskEstimate,
                analogs,
                OracleDisagreementSignal.from(oracleScore),
                pseudoRewardEnvelope,
                true);
    }

    public void update(GraphRouteState state,
                       AdaptiveRewardVector rewardVector,
                       BayesianOutcomeEstimate futureEstimate,
                       BayesianRiskEstimate riskEstimate) {
        if (state == null || rewardVector == null) {
            return;
        }
        String syntheticTraceId = "compat-" + System.nanoTime();
        PseudoRewardEnvelope pseudoRewardEnvelope = new PseudoRewardEnvelope(
                futureEstimate == null ? 0.0 : futureEstimate.postDropHitProbability(),
                0.0,
                futureEstimate == null ? 0.0 : Math.max(0.0, 1.0 - futureEstimate.expectedIdleMinutes() / 8.0),
                riskEstimate == null ? 0.0
                        : riskEstimate.lateRiskProbability() * 0.70 + riskEstimate.cancelRiskProbability() * 0.30,
                0.0);
        delegate.registerDecision(
                syntheticTraceId,
                0L,
                state,
                score(state, futureEstimate, riskEstimate, RetrievedRouteAnalogs.empty(), null),
                pseudoRewardEnvelope,
                OracleDisagreementSignal.from(null));
        delegate.resolveDecision(syntheticTraceId, 1L, rewardVector, true);
    }

    public double confidenceMean() {
        return delegate.confidenceMean();
    }

    public void reset() {
        delegate.reset();
    }
}
