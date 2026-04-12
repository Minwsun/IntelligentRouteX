package com.routechain.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class DualPenaltyController {
    private static final double MAX_PENALTY = 0.25;
    private static final double MIN_PENALTY = 0.00;
    private static final double DECAY = 0.98;
    private static final int DEFAULT_WINDOW = 20;

    private double lambdaLate = 0.10;
    private double lambdaDeadhead = 0.08;
    private double lambdaCancel = 0.08;
    private double lambdaEmptyAfter = 0.06;
    private final Deque<OutcomeVector> recentOutcomes = new ArrayDeque<>();
    private int rollingWindow = DEFAULT_WINDOW;

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
        recentOutcomes.addLast(outcome);
        while (recentOutcomes.size() > rollingWindow) {
            recentOutcomes.removeFirst();
        }
        double rollingOnTime = recentOutcomes.stream().mapToDouble(OutcomeVector::onTime).average().orElse(1.0);
        double rollingDeadhead = recentOutcomes.stream().mapToDouble(OutcomeVector::deadheadEfficiency).average().orElse(1.0);
        double rollingCancel = recentOutcomes.stream().mapToDouble(OutcomeVector::cancelAvoidance).average().orElse(1.0);
        double rollingPostDrop = recentOutcomes.stream().mapToDouble(OutcomeVector::postDropQuality).average().orElse(1.0);

        lambdaLate = adjust(lambdaLate, rollingOnTime <= 0.815 && outcome.onTime() <= 0.815, 0.02);
        lambdaDeadhead = adjust(lambdaDeadhead, rollingDeadhead <= 0.69 && outcome.deadheadEfficiency() <= 0.69, 0.02);
        lambdaCancel = adjust(lambdaCancel, rollingCancel <= 0.847 && outcome.cancelAvoidance() <= 0.847, 0.03);
        lambdaEmptyAfter = adjust(lambdaEmptyAfter, rollingPostDrop <= 0.705 && outcome.postDropQuality() <= 0.705, 0.02);
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

    public void configureWindow(int rollingWindow) {
        this.rollingWindow = Math.max(4, rollingWindow);
        while (recentOutcomes.size() > this.rollingWindow) {
            recentOutcomes.removeFirst();
        }
    }

    private double adjust(double current, boolean breached, double increaseStep) {
        double next = current * DECAY;
        if (breached) {
            next += increaseStep;
        }
        return clamp(next);
    }

    private double clamp(double value) {
        return Math.max(MIN_PENALTY, Math.min(MAX_PENALTY, value));
    }
}
