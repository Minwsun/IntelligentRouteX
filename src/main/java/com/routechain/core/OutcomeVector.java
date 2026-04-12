package com.routechain.core;

public record OutcomeVector(
        double onTime,
        double completion,
        double deadheadEfficiency,
        double profit,
        double landing,
        double postDropQuality,
        double cancelAvoidance) {

    public double totalReward() {
        double cancelPenalty = 1.0 - PlanFeatureVector.clamp01(cancelAvoidance);
        return PlanFeatureVector.clamp01(
                PlanFeatureVector.clamp01(onTime) * 0.28
                        + PlanFeatureVector.clamp01(completion) * 0.20
                        + PlanFeatureVector.clamp01(deadheadEfficiency) * 0.14
                        + PlanFeatureVector.clamp01(profit) * 0.08
                        + PlanFeatureVector.clamp01(landing) * 0.12
                        + PlanFeatureVector.clamp01(postDropQuality) * 0.10
                        - cancelPenalty * 0.08);
    }
}
