package com.routechain.ai;

import java.util.*;

/**
 * Decision journal — logs every dispatch decision with context, features, and outcome.
 * Used for:
 * - Model training (offline batch retrain)
 * - Replay analysis (what if we chose differently)
 * - Policy evaluation (which policy profile performed best)
 *
 * Ring-buffer capped at MAX_ENTRIES to bound memory.
 */
public class DecisionLog {

    private static final int MAX_ENTRIES = 10_000;

    /**
     * A single decision entry in the log.
     */
    public record DecisionEntry(
            long tick,                   // when decision was made
            double[] contextFeatures,    // city state at decision time (8D)
            double[] planFeatures,       // chosen plan features (15D)
            double predictedUtility,     // what model predicted
            double actualReward,         // what happened (-1 = pending)
            String policyUsed,           // which policy profile was active
            String traceId,              // unique decision trace ID
            long completionTick          // when outcome was recorded (-1 = pending)
    ) {
        /**
         * Create a copy with updated actual reward and completion tick.
         */
        public DecisionEntry withOutcome(double reward, long completedAt) {
            return new DecisionEntry(tick, contextFeatures, planFeatures,
                    predictedUtility, reward, policyUsed, traceId, completedAt);
        }

        public boolean isCompleted() {
            return completionTick >= 0;
        }
    }

    private final List<DecisionEntry> entries = new ArrayList<>();
    private final Map<String, Integer> traceIdIndex = new HashMap<>();

    // ── Logging ─────────────────────────────────────────────────────────

    /**
     * Log a new dispatch decision.
     */
    public synchronized void log(long tick, double[] contextFeatures, double[] planFeatures,
                                  double predictedUtility, String policyUsed, String traceId) {
        DecisionEntry entry = new DecisionEntry(
                tick, contextFeatures.clone(), planFeatures.clone(),
                predictedUtility, -1.0, policyUsed, traceId, -1);

        if (entries.size() >= MAX_ENTRIES) {
            // Remove oldest and update index
            DecisionEntry removed = entries.remove(0);
            traceIdIndex.remove(removed.traceId());
            // Shift all indices down by 1
            traceIdIndex.replaceAll((k, v) -> v - 1);
        }

        entries.add(entry);
        traceIdIndex.put(traceId, entries.size() - 1);
    }

    /**
     * Record outcome when a plan completes (or fails).
     *
     * @param traceId     the decision trace ID
     * @param actualReward actual utility (e.g., profit, on-time bonus minus penalties)
     * @param completionTick tick when outcome was observed
     */
    public synchronized void recordOutcome(String traceId, double actualReward, long completionTick) {
        Integer idx = traceIdIndex.get(traceId);
        if (idx == null || idx >= entries.size()) return;

        DecisionEntry old = entries.get(idx);
        if (old.isCompleted()) return; // already recorded

        entries.set(idx, old.withOutcome(actualReward, completionTick));
    }

    // ── Queries ─────────────────────────────────────────────────────────

    /**
     * Get all entries where outcome has been recorded.
     */
    public synchronized List<DecisionEntry> getCompletedEntries() {
        return entries.stream()
                .filter(DecisionEntry::isCompleted)
                .toList();
    }

    /**
     * Get training pairs: (planFeatures, actualReward) from completed entries.
     */
    public synchronized TrainingData getTrainingData() {
        List<DecisionEntry> completed = getCompletedEntries();
        double[][] features = new double[completed.size()][];
        double[] targets = new double[completed.size()];

        for (int i = 0; i < completed.size(); i++) {
            features[i] = completed.get(i).planFeatures();
            targets[i] = completed.get(i).actualReward();
        }
        return new TrainingData(features, targets);
    }

    /**
     * Get training pairs for policy selector: (contextFeatures, policyReward) per policy.
     */
    public synchronized Map<String, TrainingData> getPolicyTrainingData() {
        Map<String, List<double[]>> featuresByPolicy = new HashMap<>();
        Map<String, List<Double>> targetsByPolicy = new HashMap<>();

        for (DecisionEntry e : entries) {
            if (!e.isCompleted()) continue;
            featuresByPolicy.computeIfAbsent(e.policyUsed(), k -> new ArrayList<>())
                    .add(e.contextFeatures());
            targetsByPolicy.computeIfAbsent(e.policyUsed(), k -> new ArrayList<>())
                    .add(e.actualReward());
        }

        Map<String, TrainingData> result = new HashMap<>();
        for (String policy : featuresByPolicy.keySet()) {
            List<double[]> fList = featuresByPolicy.get(policy);
            List<Double> tList = targetsByPolicy.get(policy);
            double[][] fArr = fList.toArray(new double[0][]);
            double[] tArr = tList.stream().mapToDouble(Double::doubleValue).toArray();
            result.put(policy, new TrainingData(fArr, tArr));
        }
        return result;
    }

    /**
     * Get prediction error stats (MAE, RMSE) for model quality monitoring.
     */
    public synchronized ModelErrorStats getErrorStats() {
        List<DecisionEntry> completed = getCompletedEntries();
        if (completed.isEmpty()) return new ModelErrorStats(0, 0, 0);

        double sumAbsError = 0;
        double sumSqError = 0;
        for (DecisionEntry e : completed) {
            double error = e.predictedUtility() - e.actualReward();
            sumAbsError += Math.abs(error);
            sumSqError += error * error;
        }
        int n = completed.size();
        return new ModelErrorStats(
                sumAbsError / n,
                Math.sqrt(sumSqError / n),
                n);
    }

    // ── Accessors ───────────────────────────────────────────────────────

    public synchronized int size() { return entries.size(); }
    public synchronized int completedCount() { return (int) entries.stream().filter(DecisionEntry::isCompleted).count(); }

    public synchronized void clear() {
        entries.clear();
        traceIdIndex.clear();
    }

    // ── Data classes ────────────────────────────────────────────────────

    public record TrainingData(double[][] features, double[] targets) {
        public int size() { return targets.length; }
        public boolean isEmpty() { return targets.length == 0; }
    }

    public record ModelErrorStats(double mae, double rmse, int sampleCount) {}
}
