package com.routechain.ai;

import com.routechain.ai.model.*;

import java.util.Map;

/**
 * Replay Trainer — batch retrain all learned models from DecisionLog.
 * Called periodically (every 300 ticks) by OmegaDispatchAgent.
 *
 * Training protocol:
 * 1. Extract completed decision entries
 * 2. Retrain PlanRanker on (planFeatures → actualReward)
 * 3. Retrain UncertaintyEstimator on same data
 * 4. Retrain PolicySelector reward models per policy
 *
 * This is what makes the system "learn from its own decisions".
 */
public class ReplayTrainer {

    private static final int MIN_SAMPLES_TO_RETRAIN = 30;
    private static final int RETRAIN_EPOCHS = 3;
    private long lastRetrainCount = 0;

    /**
     * Batch retrain from completed decision log entries.
     */
    public void retrain(DecisionLog log, PlanRanker ranker,
                         UncertaintyEstimator uncertainty,
                         PolicySelector policySelector) {

        // Only retrain if we have enough new data
        int completed = log.completedCount();
        if (completed < MIN_SAMPLES_TO_RETRAIN) return;
        if (completed - lastRetrainCount < 20) return; // need 20+ new samples

        lastRetrainCount = completed;

        // 1. Retrain PlanRanker
        DecisionLog.TrainingData td = log.getTrainingData();
        if (!td.isEmpty()) {
            ranker.batchRetrain(td.features(), td.targets(), RETRAIN_EPOCHS);
        }

        // 2. Retrain UncertaintyEstimator on same data
        if (!td.isEmpty()) {
            uncertainty.batchTrain(td.features(), td.targets(), RETRAIN_EPOCHS);
        }

        // 3. Retrain PolicySelector per-policy reward models
        Map<String, DecisionLog.TrainingData> policyData = log.getPolicyTrainingData();
        for (var entry : policyData.entrySet()) {
            DecisionLog.TrainingData ptd = entry.getValue();
            if (!ptd.isEmpty() && ptd.size() >= 10) {
                for (int e = 0; e < RETRAIN_EPOCHS; e++) {
                    for (int i = 0; i < ptd.size(); i++) {
                        policySelector.recordReward(
                                entry.getKey(), ptd.features()[i], ptd.targets()[i]);
                    }
                }
            }
        }
    }

    /**
     * Get quality report.
     */
    public RetrainReport getReport(DecisionLog log) {
        DecisionLog.ModelErrorStats stats = log.getErrorStats();
        return new RetrainReport(
                lastRetrainCount, log.completedCount(),
                stats.mae(), stats.rmse(), stats.sampleCount());
    }

    public void reset() {
        lastRetrainCount = 0;
    }

    public record RetrainReport(
            long lastRetrainAt, int totalCompleted,
            double mae, double rmse, int evaluatedSamples) {}
}
