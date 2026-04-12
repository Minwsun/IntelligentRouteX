package com.routechain.core;

import java.time.Instant;

public record ResolvedDecisionSample(
        DecisionLogRecord decisionLog,
        OutcomeVector outcomeVector,
        DecisionOutcomeStage outcomeStage,
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
        return decisionLog.predictedReward();
    }

    public double actualReward() {
        return outcomeVector.totalReward();
    }

    public boolean eligibleForWeightUpdate() {
        return outcomeStage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW;
    }
}
