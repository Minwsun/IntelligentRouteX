package com.routechain.ai;



import java.util.*;

/**
 * Contextual Bandit Policy Selector.
 * Learns which PolicyProfile works best in which context.
 *
 * Strategy: epsilon-greedy
 * - 90% of the time: pick policy with highest predicted reward for current context
 * - 10% of the time: explore a random policy
 *
 * Each policy has its own OnlineRegressor mapping context→reward.
 * After a dispatch cycle completes, record the actual reward to train.
 */
public class PolicySelector {

    private static final double EPSILON = 0.10; // 10% exploration

    private final Map<String, OnlineRegressor> policyRewardModels = new HashMap<>();
    private final Random rng = new Random(42);
    private String lastSelectedPolicy = "NORMAL";
    private int totalSelections = 0;
    private final Map<String, Integer> selectionCounts = new HashMap<>();

    public PolicySelector() {
        // One reward model per policy (8D context → predicted reward)
        for (PolicyProfile profile : PolicyProfile.ALL) {
            policyRewardModels.put(profile.name(),
                    new OnlineRegressor(8, 0.015, 0.001, profile.name().hashCode()));
            selectionCounts.put(profile.name(), 0);
        }
    }

    /**
     * Select the best policy for the current context.
     *
     * @param contextFeatures 8D context vector
     * @return selected PolicyProfile
     */
    public PolicyProfile select(double[] contextFeatures) {
        totalSelections++;

        // Epsilon-greedy exploration
        if (rng.nextDouble() < EPSILON) {
            PolicyProfile[] all = PolicyProfile.ALL;
            PolicyProfile selected = all[rng.nextInt(all.length)];
            lastSelectedPolicy = selected.name();
            selectionCounts.merge(selected.name(), 1, Integer::sum);
            return selected;
        }

        // Exploit: pick policy with highest predicted reward
        String bestName = "NORMAL";
        double bestReward = Double.NEGATIVE_INFINITY;

        // If models not warmed up, use rule-based fallback
        boolean anyWarmedUp = policyRewardModels.values().stream()
                .anyMatch(OnlineRegressor::isWarmedUp);

        if (!anyWarmedUp) {
            PolicyProfile fallback = ruleBasedFallback(contextFeatures);
            lastSelectedPolicy = fallback.name();
            selectionCounts.merge(fallback.name(), 1, Integer::sum);
            return fallback;
        }

        for (var entry : policyRewardModels.entrySet()) {
            double predicted = entry.getValue().predict(contextFeatures);
            if (predicted > bestReward) {
                bestReward = predicted;
                bestName = entry.getKey();
            }
        }

        lastSelectedPolicy = bestName;
        selectionCounts.merge(bestName, 1, Integer::sum);
        return PolicyProfile.byName(bestName);
    }

    /**
     * Record the actual reward achieved under a policy in a given context.
     *
     * @param policyName      which policy was used
     * @param contextFeatures 8D context at decision time
     * @param actualReward    observed reward
     */
    public void recordReward(String policyName, double[] contextFeatures, double actualReward) {
        OnlineRegressor model = policyRewardModels.get(policyName);
        if (model != null) {
            model.update(contextFeatures, actualReward);
        }
    }

    /**
     * Rule-based fallback when models are cold.
     */
    private PolicyProfile ruleBasedFallback(double[] ctx) {
        double traffic = ctx[0];
        double weather = ctx[1] * 3.0;
        double shortage = ctx[3];
        double pendingRatio = ctx[5];

        if (weather > 2.0) return PolicyProfile.RAIN;
        if (shortage > 0.6) return PolicyProfile.SHORTAGE;
        if (pendingRatio > 0.7) return PolicyProfile.SPAM;
        if (traffic > 0.6) return PolicyProfile.HEAVY_TRAFFIC;
        return PolicyProfile.NORMAL;
    }

    // ── Diagnostics ─────────────────────────────────────────────────────

    public String getLastSelectedPolicy() { return lastSelectedPolicy; }
    public int getTotalSelections() { return totalSelections; }
    public Map<String, Integer> getSelectionCounts() { return Collections.unmodifiableMap(selectionCounts); }

    /**
     * Get predicted rewards for all policies given a context (for UI display).
     */
    public Map<String, Double> getPredictedRewards(double[] contextFeatures) {
        Map<String, Double> rewards = new LinkedHashMap<>();
        for (var entry : policyRewardModels.entrySet()) {
            rewards.put(entry.getKey(), entry.getValue().predict(contextFeatures));
        }
        return rewards;
    }

    public void reset() {
        for (var entry : policyRewardModels.entrySet()) {
            entry.getValue().reset(entry.getKey().hashCode());
        }
        selectionCounts.replaceAll((k, v) -> 0);
        totalSelections = 0;
        lastSelectedPolicy = "NORMAL";
    }
}
