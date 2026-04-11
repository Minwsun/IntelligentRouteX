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
        return PlanFeatureVector.clamp01(onTime) * 0.24
                + PlanFeatureVector.clamp01(completion) * 0.18
                + PlanFeatureVector.clamp01(deadheadEfficiency) * 0.16
                + PlanFeatureVector.clamp01(profit) * 0.10
                + PlanFeatureVector.clamp01(landing) * 0.12
                + PlanFeatureVector.clamp01(postDropQuality) * 0.12
                + PlanFeatureVector.clamp01(cancelAvoidance) * 0.08;
    }
}
