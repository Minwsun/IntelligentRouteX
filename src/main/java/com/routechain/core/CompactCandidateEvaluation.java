package com.routechain.core;

import com.routechain.simulation.DispatchPlan;

public record CompactCandidateEvaluation(
        DispatchPlan plan,
        PlanFeatureVector featureVector,
        AdaptiveScoreBreakdown scoreBreakdown,
        double baseConfidence) {
}
