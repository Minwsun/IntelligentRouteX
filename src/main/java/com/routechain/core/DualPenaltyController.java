package com.routechain.core;

import java.util.LinkedHashMap;
import java.util.Map;

public class DualPenaltyController {
    private static final double MAX_PENALTY = 0.60;
    private static final double MIN_PENALTY = 0.00;
    private static final double DECAY = 0.985;

    private double lambdaLate = 0.10;
    private double lambdaDeadhead = 0.08;
    private double lambdaCancel = 0.08;
    private double lambdaEmptyAfter = 0.06;

    public double scorePenalty(PlanFeatureVector phi) {
        return lambdaLate * (1.0 - phi.onTimeProbability())
                + lambdaDeadhead * phi.deadheadPenalty()
                + lambdaCancel * phi.cancelRisk()
                + lambdaEmptyAfter * phi.postCompletionEmptyKm();
    }

    public Map<String, Double> currentPenalties() {
        Map<String, Double> penalties = new LinkedHashMap<>();
        penalties.put("lambda_late", lambdaLate);
        penalties.put("lambda_deadhead", lambdaDeadhead);
        penalties.put("lambda_cancel", lambdaCancel);
        penalties.put("lambda_empty_after", lambdaEmptyAfter);
        return penalties;
    }

    public void recordOutcome(OutcomeVector outcome) {
        lambdaLate = adjust(lambdaLate, PlanFeatureVector.clamp01(0.82 - outcome.onTime()));
        lambdaDeadhead = adjust(lambdaDeadhead, PlanFeatureVector.clamp01(0.70 - outcome.deadheadEfficiency()));
        lambdaCancel = adjust(lambdaCancel, PlanFeatureVector.clamp01(0.85 - outcome.cancelAvoidance()));
        lambdaEmptyAfter = adjust(lambdaEmptyAfter, PlanFeatureVector.clamp01(0.72 - outcome.postDropQuality()));
    }

    public void decay() {
        lambdaLate = clamp(lambdaLate * DECAY);
        lambdaDeadhead = clamp(lambdaDeadhead * DECAY);
        lambdaCancel = clamp(lambdaCancel * DECAY);
        lambdaEmptyAfter = clamp(lambdaEmptyAfter * DECAY);
    }

    public void restore(Map<String, Double> penalties) {
        lambdaLate = penalties.getOrDefault("lambda_late", lambdaLate);
        lambdaDeadhead = penalties.getOrDefault("lambda_deadhead", lambdaDeadhead);
        lambdaCancel = penalties.getOrDefault("lambda_cancel", lambdaCancel);
        lambdaEmptyAfter = penalties.getOrDefault("lambda_empty_after", lambdaEmptyAfter);
    }

    private double adjust(double current, double breach) {
        double next = current * DECAY + breach * 0.05;
        return clamp(next);
    }

    private double clamp(double value) {
        return Math.max(MIN_PENALTY, Math.min(MAX_PENALTY, value));
    }
}
