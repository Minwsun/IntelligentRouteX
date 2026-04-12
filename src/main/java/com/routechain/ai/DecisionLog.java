package com.routechain.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Legacy/Omega-oriented decision journal for dispatch decisions.
 * Compact online learning should use resolved-sample records from the compact runtime path instead.
 */
public class DecisionLog {

    private static final int MAX_ENTRIES = 10_000;

    public record DecisionEntry(
            long tick,
            double[] contextFeatures,
            double[] planFeatures,
            double predictedUtility,
            double actualReward,
            String policyUsed,
            String traceId,
            String explanation,
            long completionTick
    ) {
        public DecisionEntry withOutcome(double reward, long completedAt) {
            return new DecisionEntry(
                    tick,
                    contextFeatures,
                    planFeatures,
                    predictedUtility,
                    reward,
                    policyUsed,
                    traceId,
                    explanation,
                    completedAt
            );
        }

        public boolean isCompleted() {
            return completionTick >= 0;
        }
    }

    private final List<DecisionEntry> entries = new ArrayList<>();
    private final Map<String, Integer> traceIdIndex = new HashMap<>();

    public synchronized void log(long tick,
                                 double[] contextFeatures,
                                 double[] planFeatures,
                                 double predictedUtility,
                                 String policyUsed,
                                 String traceId) {
        log(tick, contextFeatures, planFeatures, predictedUtility, policyUsed, traceId, "");
    }

    public synchronized void log(long tick,
                                 double[] contextFeatures,
                                 double[] planFeatures,
                                 double predictedUtility,
                                 String policyUsed,
                                 String traceId,
                                 String explanation) {
        DecisionEntry entry = new DecisionEntry(
                tick,
                contextFeatures.clone(),
                planFeatures.clone(),
                predictedUtility,
                -1.0,
                policyUsed,
                traceId,
                explanation,
                -1
        );

        if (entries.size() >= MAX_ENTRIES) {
            DecisionEntry removed = entries.remove(0);
            traceIdIndex.remove(removed.traceId());
            traceIdIndex.replaceAll((key, value) -> value - 1);
        }

        entries.add(entry);
        traceIdIndex.put(traceId, entries.size() - 1);
    }

    public synchronized void recordOutcome(String traceId, double actualReward, long completionTick) {
        Integer idx = traceIdIndex.get(traceId);
        if (idx == null || idx >= entries.size()) {
            return;
        }

        DecisionEntry old = entries.get(idx);
        if (old.isCompleted()) {
            return;
        }

        entries.set(idx, old.withOutcome(actualReward, completionTick));
    }

    public synchronized List<DecisionEntry> getCompletedEntries() {
        return entries.stream()
                .filter(DecisionEntry::isCompleted)
                .toList();
    }

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

    public synchronized Map<String, TrainingData> getPolicyTrainingData() {
        Map<String, List<double[]>> featuresByPolicy = new HashMap<>();
        Map<String, List<Double>> targetsByPolicy = new HashMap<>();

        for (DecisionEntry entry : entries) {
            if (!entry.isCompleted()) {
                continue;
            }
            featuresByPolicy.computeIfAbsent(entry.policyUsed(), key -> new ArrayList<>())
                    .add(entry.contextFeatures());
            targetsByPolicy.computeIfAbsent(entry.policyUsed(), key -> new ArrayList<>())
                    .add(entry.actualReward());
        }

        Map<String, TrainingData> result = new HashMap<>();
        for (String policy : featuresByPolicy.keySet()) {
            List<double[]> featureList = featuresByPolicy.get(policy);
            List<Double> targetList = targetsByPolicy.get(policy);
            result.put(
                    policy,
                    new TrainingData(
                            featureList.toArray(new double[0][]),
                            targetList.stream().mapToDouble(Double::doubleValue).toArray()
                    )
            );
        }
        return result;
    }

    public synchronized ModelErrorStats getErrorStats() {
        List<DecisionEntry> completed = getCompletedEntries();
        if (completed.isEmpty()) {
            return new ModelErrorStats(0, 0, 0);
        }

        double sumAbsError = 0.0;
        double sumSqError = 0.0;
        for (DecisionEntry entry : completed) {
            double error = entry.predictedUtility() - entry.actualReward();
            sumAbsError += Math.abs(error);
            sumSqError += error * error;
        }
        int sampleCount = completed.size();
        return new ModelErrorStats(
                sumAbsError / sampleCount,
                Math.sqrt(sumSqError / sampleCount),
                sampleCount
        );
    }

    public synchronized BehaviorStats getBehaviorStats() {
        int holdCount = 0;
        int launchCount = 0;
        int downgradeCount = 0;
        int augmentCount = 0;
        int realAssignmentCount = 0;
        int completedAssignmentCount = 0;
        int recoveredAssignmentCount = 0;
        int completedAugmentCount = 0;
        int recoveredAugmentCount = 0;

        for (DecisionEntry entry : entries) {
            if (isHoldBehavior(entry)) {
                holdCount++;
                continue;
            }

            if (isRealAssignment(entry)) {
                realAssignmentCount++;
                if (isLaunchBehavior(entry)) {
                    launchCount++;
                }
                if (isDowngradeBehavior(entry)) {
                    downgradeCount++;
                }
                if (isAugmentBehavior(entry)) {
                    augmentCount++;
                }
                if (entry.isCompleted()) {
                    completedAssignmentCount++;
                    if (entry.actualReward() >= 0.0) {
                        recoveredAssignmentCount++;
                    }
                    if (isAugmentBehavior(entry)) {
                        completedAugmentCount++;
                        if (entry.actualReward() >= 0.0) {
                            recoveredAugmentCount++;
                        }
                    }
                }
            }
        }

        int totalDecisionCount = entries.size();
        return new BehaviorStats(
                totalDecisionCount,
                realAssignmentCount,
                holdCount,
                launchCount,
                downgradeCount,
                augmentCount,
                completedAssignmentCount,
                recoveredAssignmentCount,
                completedAugmentCount,
                recoveredAugmentCount,
                rate(realAssignmentCount, totalDecisionCount),
                rate(holdCount, totalDecisionCount),
                rate(launchCount, totalDecisionCount),
                rate(downgradeCount, totalDecisionCount),
                rate(augmentCount, totalDecisionCount),
                rate(recoveredAssignmentCount, completedAssignmentCount),
                rate(recoveredAugmentCount, completedAugmentCount)
        );
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized int completedCount() {
        return (int) entries.stream().filter(DecisionEntry::isCompleted).count();
    }

    public synchronized List<DecisionEntry> getRecentEntries(int limit) {
        if (limit <= 0 || entries.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, entries.size() - limit);
        return List.copyOf(entries.subList(fromIndex, entries.size()));
    }

    public synchronized void clear() {
        entries.clear();
        traceIdIndex.clear();
    }

    private boolean isRealAssignment(DecisionEntry entry) {
        return !isHoldBehavior(entry) && entry.planFeatures().length > 0;
    }

    private boolean isHoldBehavior(DecisionEntry entry) {
        return containsExplanation(entry, "held for third order");
    }

    private boolean isLaunchBehavior(DecisionEntry entry) {
        return containsExplanation(entry, "launched clean 3-wave");
    }

    private boolean isDowngradeBehavior(DecisionEntry entry) {
        return containsExplanation(entry, "downgraded due to severe stress");
    }

    private boolean isAugmentBehavior(DecisionEntry entry) {
        if (entry.traceId() != null && entry.traceId().contains("-AUG")) {
            return true;
        }
        return containsExplanation(entry, "augment")
                || containsExplanation(entry, "extended to 4 on-route")
                || containsExplanation(entry, "extended to 5 on-route");
    }

    private boolean containsExplanation(DecisionEntry entry, String token) {
        return entry.explanation() != null
                && entry.explanation().toLowerCase(Locale.ROOT).contains(token);
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return numerator * 100.0 / denominator;
    }

    public record TrainingData(double[][] features, double[] targets) {
        public int size() {
            return targets.length;
        }

        public boolean isEmpty() {
            return targets.length == 0;
        }
    }

    public record ModelErrorStats(double mae, double rmse, int sampleCount) {}

    public record BehaviorStats(
            int totalDecisionCount,
            int realAssignmentCount,
            int holdCount,
            int launchCount,
            int downgradeCount,
            int augmentCount,
            int completedAssignmentCount,
            int recoveredAssignmentCount,
            int completedAugmentCount,
            int recoveredAugmentCount,
            double realAssignmentRate,
            double holdRate,
            double launchRate,
            double downgradeRate,
            double augmentRate,
            double recoveryRate,
            double augmentRecoveryRate
    ) {
        public String toSummary() {
            return String.format(
                    "realAssign=%.1f%% hold=%.1f%% launch=%.1f%% downgrade=%.1f%% augment=%.1f%% recover=%.1f%% augmentRecover=%.1f%%",
                    realAssignmentRate,
                    holdRate,
                    launchRate,
                    downgradeRate,
                    augmentRate,
                    recoveryRate,
                    augmentRecoveryRate
            );
        }
    }
}
