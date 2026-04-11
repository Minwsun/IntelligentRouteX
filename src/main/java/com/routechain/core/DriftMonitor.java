package com.routechain.core;

import java.util.ArrayDeque;
import java.util.Deque;

public class DriftMonitor {
    private static final int WINDOW = 20;
    private final Deque<Double> absoluteErrors = new ArrayDeque<>();
    private final Deque<Double> rewards = new ArrayDeque<>();
    private final Deque<Double> verdictPassSignals = new ArrayDeque<>();

    public record DriftAssessment(
            double rollingMae,
            double rollingRewardMean,
            double verdictPassRate,
            boolean freezeUpdates,
            boolean rollbackRecommended) {
    }

    public DriftAssessment record(double predictedReward, double actualReward, boolean passSignal) {
        push(absoluteErrors, Math.abs(actualReward - predictedReward));
        push(rewards, actualReward);
        push(verdictPassSignals, passSignal ? 1.0 : 0.0);
        double mae = mean(absoluteErrors);
        double rewardMean = mean(rewards);
        double passRate = mean(verdictPassSignals);
        boolean freeze = mae >= 0.36 || rewardMean <= 0.42;
        boolean rollback = absoluteErrors.size() >= 8 && mae >= 0.50 && passRate < 0.35;
        return new DriftAssessment(mae, rewardMean, passRate, freeze, rollback);
    }

    public DriftAssessment snapshot() {
        double mae = mean(absoluteErrors);
        double rewardMean = mean(rewards);
        double passRate = mean(verdictPassSignals);
        boolean freeze = mae >= 0.36 || rewardMean <= 0.42;
        boolean rollback = absoluteErrors.size() >= 8 && mae >= 0.50 && passRate < 0.35;
        return new DriftAssessment(mae, rewardMean, passRate, freeze, rollback);
    }

    private void push(Deque<Double> deque, double value) {
        deque.addLast(value);
        while (deque.size() > WINDOW) {
            deque.removeFirst();
        }
    }

    private double mean(Deque<Double> deque) {
        if (deque.isEmpty()) {
            return 0.0;
        }
        return deque.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
