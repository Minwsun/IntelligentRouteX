package com.routechain.core;

import java.time.Instant;

public record ResolvedDecisionSample(
        DecisionLogRecord decisionLog,
        OutcomeVector outcomeVector,
        DecisionOutcomeStage outcomeStage,
        double actualEtaMinutes,
        boolean actualCancelled,
        boolean actualPostDropHit,
        double actualPostCompletionEmptyKm,
        double actualNextOrderIdleMinutes,
        Instant resolvedAt) {

    public String decisionId() {
        return decisionLog.decisionId();
    }

    public RegimeKey regimeKey() {
        return decisionLog.regimeKey();
    }

    public PlanFeatureVector featureVector() {
        return decisionLog.featureVector();
    }

    public double predictedReward() {
        return decisionLog.predictedRewardNormalized();
    }

    public double actualReward() {
        return outcomeVector.totalReward();
    }

    public boolean eligibleForWeightUpdate() {
        return outcomeStage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW;
    }

    public boolean eligibleForEtaCalibration() {
        return outcomeStage == DecisionOutcomeStage.AFTER_TERMINAL
                || outcomeStage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW;
    }

    public boolean eligibleForCancelCalibration() {
        return outcomeStage == DecisionOutcomeStage.AFTER_ACCEPT
                || outcomeStage == DecisionOutcomeStage.AFTER_TERMINAL
                || outcomeStage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW;
    }

    public boolean eligibleForPostDropCalibration() {
        return outcomeStage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW;
    }
}
