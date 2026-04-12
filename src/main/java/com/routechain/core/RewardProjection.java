package com.routechain.core;

public final class RewardProjection {
    private RewardProjection() {
    }

    public static double project(AdaptiveScoreBreakdown breakdown, PlanFeatureVector featureVector) {
        if (breakdown == null || featureVector == null) {
            return 0.5;
        }
        double raw = breakdown.finalScore()
                + featureVector.onTimeProbability() * 0.20
                + featureVector.bundleEfficiency() * 0.08
                + featureVector.lastDropLanding() * 0.08
                - featureVector.deadheadPenalty() * 0.12
                - featureVector.cancelRisk() * 0.10
                - featureVector.postCompletionEmptyKm() * 0.08;
        return PlanFeatureVector.clamp01(1.0 / (1.0 + Math.exp(-1.35 * raw)));
    }
}
